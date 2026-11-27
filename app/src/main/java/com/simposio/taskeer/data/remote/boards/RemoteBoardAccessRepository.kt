package com.simposio.taskeer.data.remote.boards

import com.simposio.taskeer.data.remote.ApiClient
import com.simposio.taskeer.data.remote.ApiRequest
import com.simposio.taskeer.data.remote.ApiResult
import com.simposio.taskeer.data.remote.AuthMode
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.repository.BoardAccessRepository
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.workspace.BoardAccessState
import com.simposio.taskeer.domain.workspace.BoardMemberOverride
import com.simposio.taskeer.domain.workspace.BoardOverrideConfirmedOps
import com.simposio.taskeer.domain.workspace.BoardRoleRules
import com.simposio.taskeer.domain.workspace.MemberRules
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

class RemoteBoardAccessRepository(
    private val client: ApiClient,
    private val sessionState: StateFlow<SessionState>,
    private val scope: CoroutineScope,
) : BoardAccessRepository {

    // Todo el estado mutable se toca bajo `lock`, sin suspender dentro.
    private val lock = Any()
    private val mutableState = MutableStateFlow(BoardAccessState())
    private val inFlightUserIds = mutableSetOf<String>()
    private var epoch = 0
    private var knownEmail: String? = null

    // `boardKey` cambia con cada tablero abierto: lo que vuelva de otro tablero se descarta.
    private var boardKey = 0
    private var refreshJob: Deferred<WorkspaceOutcome>? = null
    private var refreshGeneration = 0
    private var appliedGeneration = 0
    private var overridesJob: Deferred<WorkspaceOutcome>? = null
    private var overridesGeneration = 0

    // Mutaciones confirmadas mientras hay una lectura de overrides en vuelo: su respuesta puede ser anterior a ellas.
    private var overridesRunning = false
    private val upsertedDuringLoad = mutableMapOf<String, BoardMemberOverride>()
    private val removedDuringLoad = mutableSetOf<String>()

    override val state: StateFlow<BoardAccessState> = mutableState.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessionState.collect { onSession(it) }
        }
    }

    override suspend fun open(workspaceId: String, boardId: String): WorkspaceOutcome {
        if (!signedIn()) return unauthenticated()
        if (!BoardRolesApiContract.isValidId(workspaceId) || !BoardRolesApiContract.isValidId(boardId)) return invalidId()
        synchronized(lock) {
            val current = mutableState.value
            if (current.workspaceId != workspaceId || current.boardId != boardId) {
                boardKey++
                refreshJob = null
                overridesJob = null
                overridesGeneration++
                clearConfirmed()
                mutableState.value = BoardAccessState(workspaceId = workspaceId, boardId = boardId)
            }
        }
        return refresh()
    }

    override suspend fun refresh(): WorkspaceOutcome {
        if (!signedIn()) return unauthenticated()
        val job = synchronized(lock) {
            val current = mutableState.value
            val workspaceId = current.workspaceId
            val boardId = current.boardId
            if (workspaceId == null || boardId == null) return boardNotFound()
            refreshJob?.takeIf { !it.isCompleted } ?: run {
                val generation = ++refreshGeneration
                val key = boardKey
                scope.async(start = CoroutineStart.LAZY) { readBoard(workspaceId, boardId, key, generation) }
                    .also { refreshJob = it }
            }
        }
        return job.await()
    }

    private suspend fun readBoard(workspaceId: String, boardId: String, key: Int, generation: Int): WorkspaceOutcome {
        val startEpoch = synchronized(lock) { epoch }
        val request = ApiRequest(HttpMethod.GET, BoardRolesApiContract.boardPath(workspaceId, boardId), auth = AuthMode.BEARER)
        return when (val result = client.execute(request)) {
            is ApiResult.Failure -> WorkspaceOutcome.Failed(result.error, result.retryAfterSeconds)
            is ApiResult.Success -> {
                val (name, role) = BoardRolesApiContract.parseBoardAccess(result.response.body) ?: return unreadable()
                synchronized(lock) {
                    if (epoch != startEpoch || boardKey != key || generation < appliedGeneration) return stale()
                    appliedGeneration = generation
                    mutableState.value = mutableState.value.copy(boardName = name, effectiveRole = role)
                }
                WorkspaceOutcome.Done
            }
        }
    }

    override suspend fun loadOverrides(): WorkspaceOutcome {
        if (!signedIn()) return unauthenticated()
        val job = synchronized(lock) {
            val current = mutableState.value
            val workspaceId = current.workspaceId
            val boardId = current.boardId
            if (workspaceId == null || boardId == null) return boardNotFound()
            overridesJob?.takeIf { !it.isCompleted } ?: run {
                val generation = ++overridesGeneration
                val key = boardKey
                scope.async(start = CoroutineStart.LAZY) { readOverrides(workspaceId, boardId, key, generation) }
                    .also { overridesJob = it }
            }
        }
        return job.await()
    }

    private suspend fun readOverrides(workspaceId: String, boardId: String, key: Int, generation: Int): WorkspaceOutcome {
        val startEpoch = synchronized(lock) {
            clearConfirmed()
            overridesRunning = true
            epoch
        }
        try {
            val request = ApiRequest(
                HttpMethod.GET,
                BoardRolesApiContract.overridesPath(workspaceId, boardId),
                auth = AuthMode.BEARER,
            )
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> WorkspaceOutcome.Failed(result.error, result.retryAfterSeconds)
                is ApiResult.Success -> {
                    val fresh = BoardRolesApiContract.parseOverrides(result.response.body) ?: return unreadable()
                    synchronized(lock) {
                        if (epoch != startEpoch || boardKey != key || overridesGeneration != generation) return stale()
                        val confirmed = BoardOverrideConfirmedOps(upsertedDuringLoad.toMap(), removedDuringLoad.toSet())
                        mutableState.value = mutableState.value.copy(
                            overrides = BoardRoleRules.reapply(fresh, confirmed),
                            overridesLoaded = true,
                        )
                    }
                    WorkspaceOutcome.Done
                }
            }
        } finally {
            synchronized(lock) {
                if (overridesGeneration == generation) {
                    overridesRunning = false
                    clearConfirmed()
                }
            }
        }
    }

    override suspend fun setOverride(userId: String, role: WorkspaceRole): WorkspaceOutcome {
        if (!signedIn()) return unauthenticated()
        if (!BoardRolesApiContract.isValidId(userId)) return invalidId()
        if (role !in MemberRules.ASSIGNABLE) return validation()
        val target = when (val found = locate()) {
            is Located.Found -> found
            is Located.Missing -> return found.failure
        }
        val startEpoch = synchronized(lock) {
            if (!inFlightUserIds.add(userId)) return busy()
            epoch
        }
        try {
            val request = ApiRequest(
                HttpMethod.PUT,
                BoardRolesApiContract.overridePath(target.workspaceId, target.boardId, userId),
                jsonBody = BoardRolesApiContract.roleBody(role),
                auth = AuthMode.BEARER,
            )
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> WorkspaceOutcome.Failed(result.error, result.retryAfterSeconds)
                is ApiResult.Success -> {
                    val saved = BoardRolesApiContract.parseOverride(result.response.body)
                        ?: return rereadAfterUnreadable(target, startEpoch)
                    synchronized(lock) {
                        if (!isCurrent(target, startEpoch)) return stale()
                        val state = mutableState.value
                        mutableState.value = state.copy(
                            overrides = (state.overrides.filter { it.userId != userId } + saved).sortedBy { it.userId },
                        )
                        if (overridesRunning) {
                            removedDuringLoad -= userId
                            upsertedDuringLoad[userId] = saved
                        }
                    }
                    WorkspaceOutcome.Done
                }
            }
        } finally {
            synchronized(lock) { inFlightUserIds -= userId }
        }
    }

    // El servidor aceptó la fijación pero la respuesta no se entiende: se relee para no mostrar un estado inventado.
    private suspend fun rereadAfterUnreadable(target: Located.Found, startEpoch: Int): WorkspaceOutcome {
        if (!synchronized(lock) { isCurrent(target, startEpoch) }) return stale()
        loadOverrides()
        return WorkspaceOutcome.Done
    }

    override suspend fun removeOverride(userId: String): WorkspaceOutcome {
        if (!signedIn()) return unauthenticated()
        if (!BoardRolesApiContract.isValidId(userId)) return invalidId()
        val target = when (val found = locate()) {
            is Located.Found -> found
            is Located.Missing -> return found.failure
        }
        val startEpoch = synchronized(lock) {
            if (!inFlightUserIds.add(userId)) return busy()
            epoch
        }
        try {
            val request = ApiRequest(
                HttpMethod.DELETE,
                BoardRolesApiContract.overridePath(target.workspaceId, target.boardId, userId),
                auth = AuthMode.BEARER,
            )
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> {
                    if (!result.error.isOverrideGone()) return WorkspaceOutcome.Failed(result.error, result.retryAfterSeconds)
                    removeConfirmed(target, userId, startEpoch)
                    loadOverrides()
                    WorkspaceOutcome.Done
                }
                is ApiResult.Success -> {
                    removeConfirmed(target, userId, startEpoch)
                    WorkspaceOutcome.Done
                }
            }
        } finally {
            synchronized(lock) { inFlightUserIds -= userId }
        }
    }

    private sealed interface Located {
        data class Found(val workspaceId: String, val boardId: String, val key: Int) : Located

        data class Missing(val failure: WorkspaceOutcome.Failed) : Located
    }

    private fun locate(): Located = synchronized(lock) {
        val state = mutableState.value
        val workspaceId = state.workspaceId
        val boardId = state.boardId
        if (workspaceId == null || boardId == null) return Located.Missing(boardNotFound())
        Located.Found(workspaceId, boardId, boardKey)
    }

    private fun isCurrent(target: Located.Found, startEpoch: Int): Boolean =
        epoch == startEpoch && boardKey == target.key

    private fun removeConfirmed(target: Located.Found, userId: String, startEpoch: Int) {
        synchronized(lock) {
            if (!isCurrent(target, startEpoch)) return
            val state = mutableState.value
            mutableState.value = state.copy(overrides = state.overrides.filter { it.userId != userId })
            if (overridesRunning) {
                upsertedDuringLoad -= userId
                removedDuringLoad += userId
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
        boardKey++
        knownEmail = email
        refreshJob = null
        overridesJob = null
        mutableState.value = BoardAccessState()
        clearConfirmed()
    }

    private fun clearConfirmed() {
        upsertedDuringLoad.clear()
        removedDuringLoad.clear()
    }

    private fun signedIn(): Boolean = sessionState.value is SessionState.SignedIn

    // Un 404 que no es de tablero ni de workspace dice que el override ya no existe: el objetivo del DELETE ya se cumplió.
    private fun DomainError.isOverrideGone(): Boolean =
        this is DomainError.NotFound && code != BOARD_NOT_FOUND && code != WORKSPACE_NOT_FOUND

    private fun unauthenticated() =
        WorkspaceOutcome.Failed(DomainError.Unauthenticated("UNAUTHENTICATED", "Inicia sesión para ver los roles"))

    private fun stale() =
        WorkspaceOutcome.Failed(DomainError.Unauthenticated("SESSION_CHANGED", "La sesión cambió durante la petición"))

    private fun unreadable() =
        WorkspaceOutcome.Failed(DomainError.Unknown("UNREADABLE_RESPONSE", "Respuesta del servidor no válida"))

    private fun invalidId() =
        WorkspaceOutcome.Failed(DomainError.Validation("VALIDATION_ERROR", "Identificador no válido"))

    private fun validation() =
        WorkspaceOutcome.Failed(DomainError.Validation("VALIDATION_ERROR", "Rol no válido para este tablero"))

    private fun boardNotFound() =
        WorkspaceOutcome.Failed(DomainError.NotFound(BOARD_NOT_FOUND, "Ese tablero ya no está disponible"))

    private fun busy() =
        WorkspaceOutcome.Failed(DomainError.Conflict("OPERATION_IN_PROGRESS", "Ya hay una operación en curso"))

    private companion object {
        const val BOARD_NOT_FOUND = "BOARD_NOT_FOUND"
        const val WORKSPACE_NOT_FOUND = "WORKSPACE_NOT_FOUND"
    }
}
