package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.WorkspaceRepository
import com.simposio.taskeer.domain.workspace.Workspace
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.domain.workspace.WorkspaceRules
import com.simposio.taskeer.domain.workspace.WorkspacesState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeWorkspaceRepository(initial: WorkspacesState = WorkspacesState()) : WorkspaceRepository {
    val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<WorkspacesState> = mutableState

    var refreshResult: WorkspaceOutcome = WorkspaceOutcome.Done
    var refreshItems: List<Workspace>? = null
    var refreshGate: CompletableDeferred<Unit>? = null
    var mutationResult: WorkspaceOutcome = WorkspaceOutcome.Done
    var mutationGate: CompletableDeferred<Unit>? = null
    var refreshCalls = 0
    val created = mutableListOf<String>()
    val renamed = mutableListOf<Pair<String, String>>()
    val deleted = mutableListOf<String>()

    override suspend fun refresh(): WorkspaceOutcome {
        refreshCalls++
        refreshGate?.await()
        refreshItems?.let { mutableState.value = WorkspacesState(WorkspaceRules.sorted(it), loaded = true) }
        return refreshResult
    }

    override suspend fun create(name: String): WorkspaceOutcome {
        created += name
        mutationGate?.await()
        return mutationResult
    }

    override suspend fun rename(id: String, name: String): WorkspaceOutcome {
        renamed += id to name
        mutationGate?.await()
        return mutationResult
    }

    override suspend fun delete(id: String): WorkspaceOutcome {
        deleted += id
        mutationGate?.await()
        return mutationResult
    }
}
