package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.WorkspaceMembersRepository
import com.simposio.taskeer.domain.workspace.MemberRules
import com.simposio.taskeer.domain.workspace.MembersState
import com.simposio.taskeer.domain.workspace.WorkspaceMember
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeWorkspaceMembersRepository(initial: MembersState = MembersState()) : WorkspaceMembersRepository {
    val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<MembersState> = mutableState

    var loadResult: WorkspaceOutcome = WorkspaceOutcome.Done
    var loadItems: List<WorkspaceMember>? = null
    var loadGate: CompletableDeferred<Unit>? = null
    var mutationResult: WorkspaceOutcome = WorkspaceOutcome.Done
    var mutationGate: CompletableDeferred<Unit>? = null
    val loads = mutableListOf<String>()
    val roleChanges = mutableListOf<Pair<String, WorkspaceRole>>()
    val removals = mutableListOf<String>()
    val transfers = mutableListOf<String>()

    override suspend fun load(workspaceId: String): WorkspaceOutcome {
        loads += workspaceId
        loadGate?.await()
        loadItems?.let { mutableState.value = MembersState(workspaceId, MemberRules.sorted(it), loaded = true) }
        return loadResult
    }

    override suspend fun changeRole(userId: String, role: WorkspaceRole): WorkspaceOutcome {
        roleChanges += userId to role
        mutationGate?.await()
        return mutationResult
    }

    override suspend fun remove(userId: String): WorkspaceOutcome {
        removals += userId
        mutationGate?.await()
        return mutationResult
    }

    override suspend fun transferOwnership(newOwnerUserId: String): WorkspaceOutcome {
        transfers += newOwnerUserId
        mutationGate?.await()
        return mutationResult
    }
}
