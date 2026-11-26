package com.simposio.taskeer.data.remote.workspaces

import com.simposio.taskeer.data.remote.ApiClient
import com.simposio.taskeer.data.remote.ApiRequest
import com.simposio.taskeer.data.remote.ApiResult
import com.simposio.taskeer.data.remote.AuthMode
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.repository.WorkspaceMembersRepository
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.workspace.MemberConfirmedOps
import com.simposio.taskeer.domain.workspace.MemberRules
import com.simposio.taskeer.domain.workspace.MembersState
import com.simposio.taskeer.domain.workspace.WorkspaceMember
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

class RemoteWorkspaceMembersRepository(
    private val client: ApiClient,
    private val sessionState: StateFlow<SessionState>,
    private val scope: CoroutineScope,
) : WorkspaceMembersRepository {

    // Todo el estado mutable se toca bajo `lock`, sin suspender dentro.
    private val lock = Any()
    private val mutableState = MutableStateFlow(MembersState())
    private val inFlightIds = mutableSetOf<String>()
    private var transferring = false
    private var epoch = 0
    private var knownEmail: String? = null
    private var loadJob: Deferred<WorkspaceOutcome>? = null
    private var loadingWorkspaceId: String? = null
    private var loadGeneration = 0

    // Mutaciones confirmadas mientras hay un load en vuelo: su respuesta puede ser anterior a ellas.
    private var loadRunning = false
    private val removedDuringLoad = mutableSetOf<String>()
    private val rolesDuringLoad = mutableMapOf<String, WorkspaceRole>()

    override val state: StateFlow<MembersState> = mutableState.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessionState.collect { onSession(it) }
        }
    }

    override suspend fun load(workspaceId: String): WorkspaceOutcome {
        if (!signedIn()) return unauthenticated()
        if (!WorkspaceMembersApiContract.isValidId(workspaceId)) return invalidId()
        val job = synchronized(lock) {
            loadJob?.takeIf { !it.isCompleted && loadingWorkspaceId == workspaceId } ?: run {
                if (mutableState.value.workspaceId != workspaceId) mutableState.value = MembersState(workspaceId = workspaceId)
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
            val request = ApiRequest(HttpMethod.GET, WorkspaceMembersApiContract.listPath(workspaceId), auth = AuthMode.BEARER)
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> WorkspaceOutcome.Failed(result.error, result.retryAfterSeconds)
                is ApiResult.Success -> {
                    val fresh = WorkspaceMembersApiContract.parseList(result.response.body) ?: return unreadable()
                    synchronized(lock) {
                        if (epoch != startEpoch || loadGeneration != generation) return stale()
                        val confirmed = MemberConfirmedOps(removedDuringLoad.toSet(), rolesDuringLoad.toMap())
                        mutableState.value = MembersState(workspaceId, MemberRules.reapply(fresh, confirmed), loaded = true)
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

    override suspend fun changeRole(userId: String, role: WorkspaceRole): WorkspaceOutcome {
        if (!signedIn()) return unauthenticated()
        if (!WorkspaceMembersApiContract.isValidId(userId)) return invalidId()
        val (workspaceId, target) = when (val found = locate(userId)) {
            is Located.Found -> found.workspaceId to found.member
            is Located.Missing -> return found.failure
        }
        if (role !in MemberRules.ASSIGNABLE) return validation()
        if (target.role !in MemberRules.ASSIGNABLE) return if (target.role == WorkspaceRole.OWNER) ownerImmutable() else validation()
        if (role == target.role) return WorkspaceOutcome.Done
        val startEpoch = synchronized(lock) {
            if (!inFlightIds.add(userId)) return busy()
            epoch
        }
        try {
            val request = ApiRequest(
                HttpMethod.PATCH,
                WorkspaceMembersApiContract.memberPath(workspaceId, userId),
                jsonBody = WorkspaceMembersApiContract.roleBody(role),
                auth = AuthMode.BEARER,
            )
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> {
                    if (result.error.isMemberNotFound()) removeConfirmed(workspaceId, userId, startEpoch)
                    WorkspaceOutcome.Failed(result.error, result.retryAfterSeconds)
                }
                is ApiResult.Success -> {
                    val updated = WorkspaceMembersApiContract.parseOne(result.response.body) ?: return unreadable()
                    synchronized(lock) {
                        if (!isCurrent(workspaceId, startEpoch)) return stale()
                        val state = mutableState.value
                        mutableState.value = state.copy(
                            items = MemberRules.sorted(state.items.map { if (it.userId == userId) updated else it }),
                        )
                        if (loadRunning) rolesDuringLoad[userId] = updated.role
                    }
                    WorkspaceOutcome.Done
                }
            }
        } finally {
            synchronized(lock) { inFlightIds -= userId }
        }
    }

    override suspend fun remove(userId: String): WorkspaceOutcome {
        if (!signedIn()) return unauthenticated()
        if (!WorkspaceMembersApiContract.isValidId(userId)) return invalidId()
        val (workspaceId, target) = when (val found = locate(userId)) {
            is Located.Found -> found.workspaceId to found.member
            is Located.Missing -> return found.failure
        }
        if (target.role !in MemberRules.ASSIGNABLE) return if (target.role == WorkspaceRole.OWNER) ownerImmutable() else validation()
        val startEpoch = synchronized(lock) {
            if (!inFlightIds.add(userId)) return busy()
            epoch
        }
        try {
            val request = ApiRequest(
                HttpMethod.DELETE,
                WorkspaceMembersApiContract.memberPath(workspaceId, userId),
                auth = AuthMode.BEARER,
            )
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> {
                    if (!result.error.isMemberNotFound()) return WorkspaceOutcome.Failed(result.error, result.retryAfterSeconds)
                    removeConfirmed(workspaceId, userId, startEpoch)
                    WorkspaceOutcome.Done
                }
                is ApiResult.Success -> {
                    removeConfirmed(workspaceId, userId, startEpoch)
                    WorkspaceOutcome.Done
                }
            }
        } finally {
            synchronized(lock) { inFlightIds -= userId }
        }
    }

    override suspend fun transferOwnership(newOwnerUserId: String): WorkspaceOutcome {
        if (!signedIn()) return unauthenticated()
        if (!WorkspaceMembersApiContract.isValidId(newOwnerUserId)) return invalidId()
        val (workspaceId, target) = when (val found = locate(newOwnerUserId)) {
            is Located.Found -> found.workspaceId to found.member
            is Located.Missing -> return found.failure
        }
        if (target.role !in MemberRules.ASSIGNABLE) return validation()
        val startEpoch = synchronized(lock) {
            if (transferring) return busy()
            transferring = true
            epoch
        }
        try {
            val request = ApiRequest(
                HttpMethod.POST,
                WorkspaceMembersApiContract.transferPath(workspaceId),
                jsonBody = WorkspaceMembersApiContract.transferBody(newOwnerUserId),
                auth = AuthMode.BEARER,
            )
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> WorkspaceOutcome.Failed(result.error, result.retryAfterSeconds)
                is ApiResult.Success -> synchronized(lock) { if (epoch != startEpoch) stale() else WorkspaceOutcome.Done }
            }
        } finally {
            synchronized(lock) { transferring = false }
        }
    }

    private sealed interface Located {
        data class Found(val workspaceId: String, val member: WorkspaceMember) : Located

        data class Missing(val failure: WorkspaceOutcome.Failed) : Located
    }

    private fun locate(userId: String): Located = synchronized(lock) {
        val state = mutableState.value
        val workspaceId = state.workspaceId
        if (workspaceId == null || !state.loaded) return Located.Missing(workspaceNotFound())
        val member = state.items.firstOrNull { it.userId == userId } ?: return Located.Missing(memberNotFound())
        Located.Found(workspaceId, member)
    }

    private fun isCurrent(workspaceId: String, startEpoch: Int): Boolean =
        epoch == startEpoch && mutableState.value.workspaceId == workspaceId

    private fun removeConfirmed(workspaceId: String, userId: String, startEpoch: Int) {
        synchronized(lock) {
            if (!isCurrent(workspaceId, startEpoch)) return
            val state = mutableState.value
            val removedSelf = MemberRules.selfOf(state.items, knownEmail)?.userId == userId
            mutableState.value = if (removedSelf) MembersState() else state.copy(items = state.items.filter { it.userId != userId })
            if (loadRunning) removedDuringLoad += userId
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
        mutableState.value = MembersState()
        clearConfirmed()
    }

    private fun clearConfirmed() {
        removedDuringLoad.clear()
        rolesDuringLoad.clear()
    }

    private fun signedIn(): Boolean = sessionState.value is SessionState.SignedIn

    private fun DomainError.isMemberNotFound(): Boolean = this is DomainError.NotFound && code == MEMBER_NOT_FOUND

    private fun unauthenticated() =
        WorkspaceOutcome.Failed(DomainError.Unauthenticated("UNAUTHENTICATED", "Inicia sesión para ver los miembros"))

    private fun stale() =
        WorkspaceOutcome.Failed(DomainError.Unauthenticated("SESSION_CHANGED", "La sesión cambió durante la petición"))

    private fun unreadable() =
        WorkspaceOutcome.Failed(DomainError.Unknown("UNREADABLE_RESPONSE", "Respuesta del servidor no válida"))

    private fun invalidId() =
        WorkspaceOutcome.Failed(DomainError.Validation("VALIDATION_ERROR", "Identificador no válido"))

    private fun validation() =
        WorkspaceOutcome.Failed(DomainError.Validation("VALIDATION_ERROR", "Operación no válida sobre este miembro"))

    private fun ownerImmutable() =
        WorkspaceOutcome.Failed(DomainError.Validation(CANNOT_CHANGE_OWNER_ROLE, "El propietario no se modifica así"))

    private fun workspaceNotFound() =
        WorkspaceOutcome.Failed(DomainError.NotFound(WORKSPACE_NOT_FOUND, "Ese workspace ya no está disponible"))

    private fun memberNotFound() =
        WorkspaceOutcome.Failed(DomainError.NotFound(MEMBER_NOT_FOUND, "Ese miembro ya no está disponible"))

    private fun busy() =
        WorkspaceOutcome.Failed(DomainError.Conflict("OPERATION_IN_PROGRESS", "Ya hay una operación en curso"))

    private companion object {
        const val WORKSPACE_NOT_FOUND = "WORKSPACE_NOT_FOUND"
        const val MEMBER_NOT_FOUND = "MEMBER_NOT_FOUND"
        const val CANNOT_CHANGE_OWNER_ROLE = "CANNOT_CHANGE_OWNER_ROLE"
    }
}
