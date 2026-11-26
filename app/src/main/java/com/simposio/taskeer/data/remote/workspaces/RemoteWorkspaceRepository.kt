package com.simposio.taskeer.data.remote.workspaces

import com.simposio.taskeer.data.remote.ApiClient
import com.simposio.taskeer.data.remote.ApiRequest
import com.simposio.taskeer.data.remote.ApiResult
import com.simposio.taskeer.data.remote.AuthMode
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.repository.WorkspaceRepository
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.workspace.ConfirmedOps
import com.simposio.taskeer.domain.workspace.NameCheck
import com.simposio.taskeer.domain.workspace.Workspace
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.domain.workspace.WorkspaceRules
import com.simposio.taskeer.domain.workspace.WorkspacesState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RemoteWorkspaceRepository(
    private val client: ApiClient,
    private val sessionState: StateFlow<SessionState>,
    private val scope: CoroutineScope,
) : WorkspaceRepository {

    // Todo el estado mutable se toca bajo `lock`, sin suspender dentro.
    private val lock = Any()
    private val mutableState = MutableStateFlow(WorkspacesState())
    private val inFlightIds = mutableSetOf<String>()
    private var creating = false
    private var epoch = 0
    private var knownEmail: String? = null
    private var refreshJob: Deferred<WorkspaceOutcome>? = null

    // Mutaciones confirmadas mientras hay un refresco en vuelo: su respuesta puede ser anterior a ellas.
    private var refreshRunning = false
    private val deletedDuringRefresh = mutableSetOf<String>()
    private val renamedDuringRefresh = mutableMapOf<String, String>()
    private val createdDuringRefresh = mutableListOf<Workspace>()

    override val state: StateFlow<WorkspacesState> = mutableState.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessionState.collect { onSession(it) }
        }
    }

    override suspend fun refresh(): WorkspaceOutcome {
        if (!signedIn()) return unauthenticated()
        val job = synchronized(lock) {
            refreshJob?.takeIf { !it.isCompleted } ?: scope.async(start = CoroutineStart.LAZY) { loadList() }
                .also { refreshJob = it }
        }
        return job.await()
    }

    private suspend fun loadList(): WorkspaceOutcome {
        val startEpoch = synchronized(lock) {
            clearConfirmed()
            refreshRunning = true
            epoch
        }
        try {
            val request = ApiRequest(HttpMethod.GET, WorkspacesApiContract.LIST_PATH, auth = AuthMode.BEARER)
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> WorkspaceOutcome.Failed(result.error, result.retryAfterSeconds)
                is ApiResult.Success -> {
                    val fresh = WorkspacesApiContract.parseList(result.response.body) ?: return unreadable()
                    synchronized(lock) {
                        if (epoch != startEpoch) return stale()
                        val confirmed = ConfirmedOps(
                            deletedIds = deletedDuringRefresh.toSet(),
                            renamed = renamedDuringRefresh.toMap(),
                            created = createdDuringRefresh.toList(),
                        )
                        mutableState.value = WorkspacesState(WorkspaceRules.reapply(fresh, confirmed), loaded = true)
                    }
                    WorkspaceOutcome.Done
                }
            }
        } finally {
            synchronized(lock) {
                refreshRunning = false
                clearConfirmed()
            }
        }
    }

    override suspend fun create(name: String): WorkspaceOutcome {
        if (!signedIn()) return unauthenticated()
        val valid = when (val check = WorkspaceRules.validateName(name)) {
            is NameCheck.Valid -> check.name
            NameCheck.TooShort, NameCheck.TooLong -> return invalidName()
        }
        val startEpoch = synchronized(lock) {
            if (creating) return busy()
            creating = true
            epoch
        }
        try {
            val request = ApiRequest(
                HttpMethod.POST,
                WorkspacesApiContract.LIST_PATH,
                jsonBody = WorkspacesApiContract.nameBody(valid),
                auth = AuthMode.BEARER,
            )
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> WorkspaceOutcome.Failed(result.error, result.retryAfterSeconds)
                is ApiResult.Success -> {
                    val created = WorkspacesApiContract.parseOne(result.response.body, WorkspaceRole.OWNER)
                        ?.copy(isPersonal = false) ?: return unreadable()
                    synchronized(lock) {
                        if (epoch != startEpoch) return stale()
                        val current = mutableState.value
                        mutableState.value = current.copy(
                            items = WorkspaceRules.sorted(current.items.filter { it.id != created.id } + created),
                        )
                        if (refreshRunning) createdDuringRefresh += created
                    }
                    WorkspaceOutcome.Done
                }
            }
        } finally {
            synchronized(lock) { creating = false }
        }
    }

    override suspend fun rename(id: String, name: String): WorkspaceOutcome {
        if (!signedIn()) return unauthenticated()
        if (!WorkspacesApiContract.isValidId(id)) return invalidId()
        val current = synchronized(lock) { mutableState.value.items.firstOrNull { it.id == id } } ?: return notFound()
        if (!WorkspaceRules.canRename(current.myRole)) return forbidden()
        val valid = when (val check = WorkspaceRules.validateName(name)) {
            is NameCheck.Valid -> check.name
            NameCheck.TooShort, NameCheck.TooLong -> return invalidName()
        }
        if (valid == current.name) return WorkspaceOutcome.Done
        val startEpoch = synchronized(lock) {
            if (!inFlightIds.add(id)) return busy()
            epoch
        }
        try {
            val request = ApiRequest(
                HttpMethod.PATCH,
                WorkspacesApiContract.itemPath(id),
                jsonBody = WorkspacesApiContract.nameBody(valid),
                auth = AuthMode.BEARER,
            )
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> {
                    if (result.error.isWorkspaceNotFound()) removeConfirmed(id, startEpoch)
                    WorkspaceOutcome.Failed(result.error, result.retryAfterSeconds)
                }
                is ApiResult.Success -> {
                    val updated = WorkspacesApiContract.parseOne(result.response.body, current.myRole) ?: return unreadable()
                    synchronized(lock) {
                        if (epoch != startEpoch) return stale()
                        val state = mutableState.value
                        mutableState.value = state.copy(
                            items = WorkspaceRules.sorted(
                                state.items.map {
                                    if (it.id == id) it.copy(name = updated.name, slug = updated.slug, updatedAt = updated.updatedAt) else it
                                },
                            ),
                        )
                        if (refreshRunning) renamedDuringRefresh[id] = updated.name
                    }
                    WorkspaceOutcome.Done
                }
            }
        } finally {
            synchronized(lock) { inFlightIds -= id }
        }
    }

    override suspend fun delete(id: String): WorkspaceOutcome {
        if (!signedIn()) return unauthenticated()
        if (!WorkspacesApiContract.isValidId(id)) return invalidId()
        val current = synchronized(lock) { mutableState.value.items.firstOrNull { it.id == id } } ?: return WorkspaceOutcome.Done
        if (!WorkspaceRules.canDelete(current.myRole, current.isPersonal)) return forbidden()
        val startEpoch = synchronized(lock) {
            if (!inFlightIds.add(id)) return busy()
            epoch
        }
        try {
            val request = ApiRequest(HttpMethod.DELETE, WorkspacesApiContract.itemPath(id), auth = AuthMode.BEARER)
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> {
                    if (!result.error.isWorkspaceNotFound()) return WorkspaceOutcome.Failed(result.error, result.retryAfterSeconds)
                    removeConfirmed(id, startEpoch)
                    WorkspaceOutcome.Done
                }
                is ApiResult.Success -> {
                    removeConfirmed(id, startEpoch)
                    WorkspaceOutcome.Done
                }
            }
        } finally {
            synchronized(lock) { inFlightIds -= id }
        }
    }

    private fun removeConfirmed(id: String, startEpoch: Int) {
        synchronized(lock) {
            if (epoch != startEpoch) return
            val state = mutableState.value
            mutableState.value = state.copy(items = state.items.filter { it.id != id })
            if (refreshRunning) deletedDuringRefresh += id
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
        mutableState.value = WorkspacesState()
        clearConfirmed()
    }

    private fun clearConfirmed() {
        deletedDuringRefresh.clear()
        renamedDuringRefresh.clear()
        createdDuringRefresh.clear()
    }

    private fun signedIn(): Boolean = sessionState.value is SessionState.SignedIn

    private fun DomainError.isWorkspaceNotFound(): Boolean = this is DomainError.NotFound && code == WORKSPACE_NOT_FOUND

    private fun unauthenticated() =
        WorkspaceOutcome.Failed(DomainError.Unauthenticated("UNAUTHENTICATED", "Inicia sesión para ver tus workspaces"))

    private fun stale() =
        WorkspaceOutcome.Failed(DomainError.Unauthenticated("SESSION_CHANGED", "La sesión cambió durante la petición"))

    private fun unreadable() =
        WorkspaceOutcome.Failed(DomainError.Unknown("UNREADABLE_RESPONSE", "Respuesta del servidor no válida"))

    private fun invalidName() =
        WorkspaceOutcome.Failed(DomainError.Validation("VALIDATION_ERROR", "El nombre del workspace no es válido"))

    private fun invalidId() =
        WorkspaceOutcome.Failed(DomainError.Validation("VALIDATION_ERROR", "Identificador de workspace no válido"))

    private fun forbidden() =
        WorkspaceOutcome.Failed(DomainError.Forbidden("FORBIDDEN", "Tu rol no permite esta acción"))

    private fun notFound() =
        WorkspaceOutcome.Failed(DomainError.NotFound(WORKSPACE_NOT_FOUND, "Ese workspace ya no está disponible"))

    private fun busy() =
        WorkspaceOutcome.Failed(DomainError.Conflict("OPERATION_IN_PROGRESS", "Ya hay una operación en curso"))

    private companion object {
        const val WORKSPACE_NOT_FOUND = "WORKSPACE_NOT_FOUND"
    }
}
