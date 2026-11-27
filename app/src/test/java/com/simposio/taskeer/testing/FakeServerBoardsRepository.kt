package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.ServerBoardsRepository
import com.simposio.taskeer.domain.workspace.BoardRoleRules
import com.simposio.taskeer.domain.workspace.ServerBoard
import com.simposio.taskeer.domain.workspace.ServerBoardsState
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeServerBoardsRepository(initial: ServerBoardsState = ServerBoardsState()) : ServerBoardsRepository {
    val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<ServerBoardsState> = mutableState

    var loadResult: WorkspaceOutcome = WorkspaceOutcome.Done
    var loadItems: List<ServerBoard>? = null
    var loadGate: CompletableDeferred<Unit>? = null
    val loads = mutableListOf<String>()

    override suspend fun load(workspaceId: String): WorkspaceOutcome {
        loads += workspaceId
        loadGate?.await()
        loadItems?.takeIf { loadResult == WorkspaceOutcome.Done }?.let {
            mutableState.value = ServerBoardsState(workspaceId, BoardRoleRules.sortedBoards(it), loaded = true)
        }
        return loadResult
    }
}
