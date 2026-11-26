package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.workspace.ServerBoardsState
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import kotlinx.coroutines.flow.StateFlow

// Tableros del servidor de un workspace de cuenta: estado solo en memoria, de un único workspace a la vez.
interface ServerBoardsRepository {
    val state: StateFlow<ServerBoardsState>

    suspend fun load(workspaceId: String): WorkspaceOutcome
}
