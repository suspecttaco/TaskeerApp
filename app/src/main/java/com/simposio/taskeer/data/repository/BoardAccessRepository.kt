package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.workspace.BoardAccessState
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import kotlinx.coroutines.flow.StateFlow

// Rol efectivo y overrides de un tablero del servidor: estado solo en memoria, de un único tablero a la vez.
interface BoardAccessRepository {
    val state: StateFlow<BoardAccessState>

    // Vacía el estado si cambia de tablero y lee su nombre y el rol efectivo del usuario.
    suspend fun open(workspaceId: String, boardId: String): WorkspaceOutcome

    suspend fun refresh(): WorkspaceOutcome

    suspend fun loadOverrides(): WorkspaceOutcome

    suspend fun setOverride(userId: String, role: WorkspaceRole): WorkspaceOutcome

    suspend fun removeOverride(userId: String): WorkspaceOutcome
}
