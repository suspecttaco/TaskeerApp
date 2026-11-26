package com.simposio.taskeer.data.remote.workspaces

import com.simposio.taskeer.data.remote.ApiClient
import com.simposio.taskeer.data.remote.ApiRequest
import com.simposio.taskeer.data.remote.ApiResult
import com.simposio.taskeer.data.remote.AuthMode
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.repository.MyInvitationsRepository
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.workspace.AcceptOutcome
import com.simposio.taskeer.domain.workspace.InvitationRules
import com.simposio.taskeer.domain.workspace.MyInvitationsState
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RemoteMyInvitationsRepository(
    private val client: ApiClient,
    private val sessionState: StateFlow<SessionState>,
    private val scope: CoroutineScope,
) : MyInvitationsRepository {

    // Todo el estado mutable se toca bajo `lock`, sin suspender dentro.
    private val lock = Any()
    private val mutableState = MutableStateFlow(MyInvitationsState())
    private val inFlightIds = mutableSetOf<String>()
    private var acceptingWithLink = false
    private var epoch = 0
    private var knownEmail: String? = null
    private var refreshJob: Deferred<WorkspaceOutcome>? = null
    private var refreshGeneration = 0

    // Aceptaciones confirmadas mientras hay un refresh en vuelo: su respuesta puede ser anterior a ellas.
    private var refreshRunning = false
    private val removedDuringRefresh = mutableSetOf<String>()

    override val state: StateFlow<MyInvitationsState> = mutableState.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessionState.collect { onSession(it) }
        }
    }

    override suspend fun refresh(): WorkspaceOutcome {
        if (!signedIn()) return unauthenticated()
        val job = synchronized(lock) {
            refreshJob?.takeIf { !it.isCompleted } ?: run {
                val generation = ++refreshGeneration
                scope.async(start = CoroutineStart.LAZY) { loadMine(generation) }.also { refreshJob = it }
            }
        }
        return job.await()
    }

    private suspend fun loadMine(generation: Int): WorkspaceOutcome {
        val startEpoch = synchronized(lock) {
            removedDuringRefresh.clear()
            refreshRunning = true
            epoch
        }
        try {
            val request = ApiRequest(HttpMethod.GET, InvitationsApiContract.MINE_PATH, auth = AuthMode.BEARER)
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> WorkspaceOutcome.Failed(result.error, result.retryAfterSeconds)
                is ApiResult.Success -> {
                    val fresh = InvitationsApiContract.parseMine(result.response.body) ?: return unreadable()
                    synchronized(lock) {
                        if (epoch != startEpoch || refreshGeneration != generation) return stale()
                        mutableState.value = MyInvitationsState(
                            InvitationRules.reapplyMine(fresh, removedDuringRefresh.toSet()),
                            loaded = true,
                        )
                    }
                    WorkspaceOutcome.Done
                }
            }
        } finally {
            synchronized(lock) {
                if (refreshGeneration == generation) {
                    refreshRunning = false
                    removedDuringRefresh.clear()
                }
            }
        }
    }

    override suspend fun accept(invitationId: String): AcceptOutcome {
        if (!signedIn()) return AcceptOutcome.Failed(unauthenticatedError())
        if (!InvitationsApiContract.isValidId(invitationId)) return AcceptOutcome.Failed(invalidIdError())
        val startEpoch = synchronized(lock) {
            if (!inFlightIds.add(invitationId)) return AcceptOutcome.Failed(busyError())
            epoch
        }
        try {
            val request = ApiRequest(HttpMethod.POST, InvitationsApiContract.acceptPath(invitationId), auth = AuthMode.BEARER)
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> AcceptOutcome.Failed(result.error, result.retryAfterSeconds)
                is ApiResult.Success -> {
                    val accepted = InvitationsApiContract.parseAccepted(result.response.body)
                    synchronized(lock) {
                        if (epoch != startEpoch) return AcceptOutcome.Failed(staleError())
                        val state = mutableState.value
                        mutableState.value = state.copy(items = state.items.filter { it.id != invitationId })
                        if (refreshRunning) removedDuringRefresh += invitationId
                    }
                    AcceptOutcome.Accepted(accepted.name, accepted.alreadyMember)
                }
            }
        } finally {
            synchronized(lock) { inFlightIds -= invitationId }
        }
    }

    // El token solo vive en este parámetro y en la query de la petición: no se asigna a ningún campo.
    override suspend fun acceptWithToken(token: String): AcceptOutcome {
        if (!signedIn()) return AcceptOutcome.Failed(unauthenticatedError())
        if (token.isBlank() || token.any { it.isWhitespace() }) return AcceptOutcome.Failed(invalidTokenError())
        val startEpoch = synchronized(lock) {
            if (acceptingWithLink) return AcceptOutcome.Failed(busyError())
            acceptingWithLink = true
            epoch
        }
        try {
            val request = ApiRequest(
                HttpMethod.POST,
                InvitationsApiContract.ACCEPT_WITH_TOKEN_PATH,
                query = listOf(InvitationsApiContract.TOKEN_QUERY to token),
                auth = AuthMode.BEARER,
            )
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> AcceptOutcome.Failed(result.error, result.retryAfterSeconds)
                is ApiResult.Success -> {
                    val accepted = InvitationsApiContract.parseAccepted(result.response.body)
                    if (synchronized(lock) { epoch != startEpoch }) return AcceptOutcome.Failed(staleError())
                    AcceptOutcome.Accepted(accepted.name, accepted.alreadyMember)
                }
            }
        } finally {
            synchronized(lock) { acceptingWithLink = false }
        }
    }

    private fun onSession(session: SessionState) {
        synchronized(lock) {
            when (session) {
                is SessionState.SignedOut -> reset(email = null)
                is SessionState.SignedIn -> {
                    val email = session.account?.email
                    if (knownEmail != null && email != null && email != knownEmail) reset(email) else knownEmail = email ?: knownEmail
                }
                SessionState.Resuming -> Unit
            }
        }
    }

    private fun reset(email: String?) {
        epoch++
        knownEmail = email
        mutableState.value = MyInvitationsState()
        removedDuringRefresh.clear()
    }

    private fun signedIn(): Boolean = sessionState.value is SessionState.SignedIn

    private fun unauthenticatedError() = DomainError.Unauthenticated("UNAUTHENTICATED", "Inicia sesión para ver las invitaciones")

    private fun staleError() = DomainError.Unauthenticated("SESSION_CHANGED", "La sesión cambió durante la petición")

    private fun invalidIdError() = DomainError.Validation("VALIDATION_ERROR", "Identificador no válido")

    private fun invalidTokenError() = DomainError.Validation("VALIDATION_ERROR", "Enlace de invitación no válido")

    private fun busyError() = DomainError.Conflict("OPERATION_IN_PROGRESS", "Ya hay una operación en curso")

    private fun unauthenticated() = WorkspaceOutcome.Failed(unauthenticatedError())

    private fun stale() = WorkspaceOutcome.Failed(staleError())

    private fun unreadable() =
        WorkspaceOutcome.Failed(DomainError.Unknown("UNREADABLE_RESPONSE", "Respuesta del servidor no válida"))
}
