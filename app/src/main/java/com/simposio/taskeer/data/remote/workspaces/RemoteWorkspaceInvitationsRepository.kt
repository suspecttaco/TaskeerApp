package com.simposio.taskeer.data.remote.workspaces

import com.simposio.taskeer.data.remote.ApiClient
import com.simposio.taskeer.data.remote.ApiRequest
import com.simposio.taskeer.data.remote.ApiResult
import com.simposio.taskeer.data.remote.AuthMode
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.repository.WorkspaceInvitationsRepository
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.workspace.Invitation
import com.simposio.taskeer.domain.workspace.InvitationConfirmedOps
import com.simposio.taskeer.domain.workspace.InvitationRules
import com.simposio.taskeer.domain.workspace.InvitationsState
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RemoteWorkspaceInvitationsRepository(
    private val client: ApiClient,
    private val sessionState: StateFlow<SessionState>,
    private val scope: CoroutineScope,
) : WorkspaceInvitationsRepository {

    // Todo el estado mutable se toca bajo `lock`, sin suspender dentro.
    private val lock = Any()
    private val mutableState = MutableStateFlow(InvitationsState())
    private val inFlightIds = mutableSetOf<String>()
    private var inviting = false
    private var epoch = 0
    private var knownEmail: String? = null
    private var loadJob: Deferred<WorkspaceOutcome>? = null
    private var loadingWorkspaceId: String? = null
    private var loadGeneration = 0

    // Mutaciones confirmadas mientras hay un load en vuelo: su respuesta puede ser anterior a ellas.
    private var loadRunning = false
    private val removedDuringLoad = mutableSetOf<String>()
    private val addedDuringLoad = mutableListOf<Invitation>()

    override val state: StateFlow<InvitationsState> = mutableState.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessionState.collect { onSession(it) }
        }
    }

    override suspend fun load(workspaceId: String): WorkspaceOutcome {
        if (!signedIn()) return unauthenticated()
        if (!InvitationsApiContract.isValidId(workspaceId)) return invalidId()
        val job = synchronized(lock) {
            loadJob?.takeIf { !it.isCompleted && loadingWorkspaceId == workspaceId } ?: run {
                if (mutableState.value.workspaceId != workspaceId) mutableState.value = InvitationsState(workspaceId = workspaceId)
                val generation = ++loadGeneration
                loadingWorkspaceId = workspaceId
                scope.async(start = CoroutineStart.LAZY) { loadList(workspaceId, generation) }.also { loadJob = it }
            }
        }
        return job.await()
    }

    private suspend fun loadList(workspaceId: String, generation: Int): WorkspaceOutcome {
        val startEpoch = synchronized(lock) {
            clearConfirmed()
            loadRunning = true
            epoch
        }
        try {
            val request = ApiRequest(HttpMethod.GET, InvitationsApiContract.listPath(workspaceId), auth = AuthMode.BEARER)
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> WorkspaceOutcome.Failed(result.error, result.retryAfterSeconds)
                is ApiResult.Success -> {
                    val fresh = InvitationsApiContract.parseList(result.response.body) ?: return unreadable()
                    synchronized(lock) {
                        if (epoch != startEpoch || loadGeneration != generation) return stale()
                        val confirmed = InvitationConfirmedOps(removedDuringLoad.toSet(), addedDuringLoad.toList())
                        mutableState.value = InvitationsState(workspaceId, InvitationRules.reapply(fresh, confirmed), loaded = true)
                    }
                    WorkspaceOutcome.Done
                }
            }
        } finally {
            synchronized(lock) {
                if (loadGeneration == generation) {
                    loadRunning = false
                    clearConfirmed()
                }
            }
        }
    }

    override suspend fun invite(email: String, role: WorkspaceRole): WorkspaceOutcome {
        if (!signedIn()) return unauthenticated()
        val workspaceId = synchronized(lock) { mutableState.value.workspaceId } ?: return workspaceNotFound()
        if (role !in InvitationRules.INVITABLE || email.isBlank()) return validation()
        val startEpoch = synchronized(lock) {
            if (inviting) return busy()
            inviting = true
            epoch
        }
        try {
            val request = ApiRequest(
                HttpMethod.POST,
                InvitationsApiContract.listPath(workspaceId),
                jsonBody = InvitationsApiContract.inviteBody(email, role),
                auth = AuthMode.BEARER,
            )
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> WorkspaceOutcome.Failed(result.error, result.retryAfterSeconds)
                is ApiResult.Success -> {
                    val created = InvitationsApiContract.parseOne(result.response.body) ?: return unreadable()
                    synchronized(lock) {
                        if (!isCurrent(workspaceId, startEpoch)) return stale()
                        val state = mutableState.value
                        mutableState.value = state.copy(
                            items = InvitationRules.sorted(state.items.filter { it.id != created.id } + created),
                        )
                        if (loadRunning) addedDuringLoad += created
                    }
                    WorkspaceOutcome.Done
                }
            }
        } finally {
            synchronized(lock) { inviting = false }
        }
    }

    override suspend fun revoke(invitationId: String): WorkspaceOutcome {
        if (!signedIn()) return unauthenticated()
        if (!InvitationsApiContract.isValidId(invitationId)) return invalidId()
        val workspaceId = when (val found = locate(invitationId)) {
            is Located.Found -> found.workspaceId
            is Located.Missing -> return found.failure
        }
        val startEpoch = synchronized(lock) {
            if (!inFlightIds.add(invitationId)) return busy()
            epoch
        }
        try {
            val request = ApiRequest(
                HttpMethod.DELETE,
                InvitationsApiContract.invitationPath(workspaceId, invitationId),
                auth = AuthMode.BEARER,
            )
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> {
                    if (!result.error.isInvitationNotFound()) return WorkspaceOutcome.Failed(result.error, result.retryAfterSeconds)
                    removeConfirmed(workspaceId, invitationId, startEpoch)
                    WorkspaceOutcome.Done
                }
                is ApiResult.Success -> {
                    removeConfirmed(workspaceId, invitationId, startEpoch)
                    WorkspaceOutcome.Done
                }
            }
        } finally {
            synchronized(lock) { inFlightIds -= invitationId }
        }
    }

    private sealed interface Located {
        data class Found(val workspaceId: String) : Located

        data class Missing(val failure: WorkspaceOutcome.Failed) : Located
    }

    private fun locate(invitationId: String): Located = synchronized(lock) {
        val state = mutableState.value
        val workspaceId = state.workspaceId
        if (workspaceId == null || !state.loaded) return Located.Missing(workspaceNotFound())
        if (state.items.none { it.id == invitationId }) return Located.Missing(invitationNotFound())
        Located.Found(workspaceId)
    }

    private fun isCurrent(workspaceId: String, startEpoch: Int): Boolean =
        epoch == startEpoch && mutableState.value.workspaceId == workspaceId

    private fun removeConfirmed(workspaceId: String, invitationId: String, startEpoch: Int) {
        synchronized(lock) {
            if (!isCurrent(workspaceId, startEpoch)) return
            val state = mutableState.value
            mutableState.value = state.copy(items = state.items.filter { it.id != invitationId })
            if (loadRunning) removedDuringLoad += invitationId
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
        mutableState.value = InvitationsState()
        clearConfirmed()
    }

    private fun clearConfirmed() {
        removedDuringLoad.clear()
        addedDuringLoad.clear()
    }

    private fun signedIn(): Boolean = sessionState.value is SessionState.SignedIn

    // El servidor responde 404 con un código propio por cada recurso; solo el del workspace es un fallo real.
    private fun DomainError.isInvitationNotFound(): Boolean = this is DomainError.NotFound && code != WORKSPACE_NOT_FOUND

    private fun unauthenticated() =
        WorkspaceOutcome.Failed(DomainError.Unauthenticated("UNAUTHENTICATED", "Inicia sesión para ver las invitaciones"))

    private fun stale() =
        WorkspaceOutcome.Failed(DomainError.Unauthenticated("SESSION_CHANGED", "La sesión cambió durante la petición"))

    private fun unreadable() =
        WorkspaceOutcome.Failed(DomainError.Unknown("UNREADABLE_RESPONSE", "Respuesta del servidor no válida"))

    private fun invalidId() =
        WorkspaceOutcome.Failed(DomainError.Validation("VALIDATION_ERROR", "Identificador no válido"))

    private fun validation() =
        WorkspaceOutcome.Failed(DomainError.Validation("VALIDATION_ERROR", "Invitación no válida"))

    private fun workspaceNotFound() =
        WorkspaceOutcome.Failed(DomainError.NotFound(WORKSPACE_NOT_FOUND, "Ese workspace ya no está disponible"))

    private fun invitationNotFound() =
        WorkspaceOutcome.Failed(DomainError.NotFound(INVITATION_NOT_FOUND, "Esa invitación ya no está disponible"))

    private fun busy() =
        WorkspaceOutcome.Failed(DomainError.Conflict("OPERATION_IN_PROGRESS", "Ya hay una operación en curso"))

    private companion object {
        const val WORKSPACE_NOT_FOUND = "WORKSPACE_NOT_FOUND"
        const val INVITATION_NOT_FOUND = "INVITATION_NOT_FOUND"
    }
}
