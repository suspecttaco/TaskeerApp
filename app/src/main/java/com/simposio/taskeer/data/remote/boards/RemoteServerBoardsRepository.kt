package com.simposio.taskeer.data.remote.boards

import com.simposio.taskeer.data.remote.ApiClient
import com.simposio.taskeer.data.remote.ApiRequest
import com.simposio.taskeer.data.remote.ApiResult
import com.simposio.taskeer.data.remote.AuthMode
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.repository.ServerBoardsRepository
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.workspace.BoardRoleRules
import com.simposio.taskeer.domain.workspace.ServerBoardsState
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RemoteServerBoardsRepository(
    private val client: ApiClient,
    private val sessionState: StateFlow<SessionState>,
    private val scope: CoroutineScope,
) : ServerBoardsRepository {

    // Todo el estado mutable se toca bajo `lock`, sin suspender dentro.
    private val lock = Any()
    private val mutableState = MutableStateFlow(ServerBoardsState())
    private var epoch = 0
    private var knownEmail: String? = null
    private var loadJob: Deferred<WorkspaceOutcome>? = null
    private var loadingWorkspaceId: String? = null
    private var loadGeneration = 0

    override val state: StateFlow<ServerBoardsState> = mutableState.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessionState.collect { onSession(it) }
        }
    }

    override suspend fun load(workspaceId: String): WorkspaceOutcome {
        if (!signedIn()) return unauthenticated()
        if (!BoardRolesApiContract.isValidId(workspaceId)) return invalidId()
        val job = synchronized(lock) {
            loadJob?.takeIf { !it.isCompleted && loadingWorkspaceId == workspaceId } ?: run {
                if (mutableState.value.workspaceId != workspaceId) {
                    mutableState.value = ServerBoardsState(workspaceId = workspaceId)
                }
                val generation = ++loadGeneration
                loadingWorkspaceId = workspaceId
                scope.async(start = CoroutineStart.LAZY) { loadList(workspaceId, generation) }.also { loadJob = it }
            }
        }
        return job.await()
    }

    private suspend fun loadList(workspaceId: String, generation: Int): WorkspaceOutcome {
        val startEpoch = synchronized(lock) { epoch }
        val request = ApiRequest(HttpMethod.GET, BoardRolesApiContract.boardsPath(workspaceId), auth = AuthMode.BEARER)
        return when (val result = client.execute(request)) {
            is ApiResult.Failure -> WorkspaceOutcome.Failed(result.error, result.retryAfterSeconds)
            is ApiResult.Success -> {
                val fresh = BoardRolesApiContract.parseBoards(result.response.body) ?: return unreadable()
                synchronized(lock) {
                    if (epoch != startEpoch || loadGeneration != generation) return stale()
                    mutableState.value = ServerBoardsState(workspaceId, BoardRoleRules.sortedBoards(fresh), loaded = true)
                }
                WorkspaceOutcome.Done
            }
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
        mutableState.value = ServerBoardsState()
    }

    private fun signedIn(): Boolean = sessionState.value is SessionState.SignedIn

    private fun unauthenticated() =
        WorkspaceOutcome.Failed(DomainError.Unauthenticated("UNAUTHENTICATED", "Inicia sesión para ver los tableros"))

    private fun stale() =
        WorkspaceOutcome.Failed(DomainError.Unauthenticated("SESSION_CHANGED", "La sesión cambió durante la petición"))

    private fun unreadable() =
        WorkspaceOutcome.Failed(DomainError.Unknown("UNREADABLE_RESPONSE", "Respuesta del servidor no válida"))

    private fun invalidId() =
        WorkspaceOutcome.Failed(DomainError.Validation("VALIDATION_ERROR", "Identificador no válido"))
}
