package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.BoardAccessDao
import com.simposio.taskeer.domain.workspace.BoardRoleRules
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

// Rol efectivo que el servidor resolvió para el usuario en un tablero. Null = tablero local propio, sin restricciones.
interface BoardRoles {
    suspend fun roleOf(boardId: String): WorkspaceRole?

    fun observeRole(boardId: String): Flow<WorkspaceRole?>
}

object UnrestrictedBoardRoles : BoardRoles {
    override suspend fun roleOf(boardId: String): WorkspaceRole? = null

    override fun observeRole(boardId: String): Flow<WorkspaceRole?> = flowOf(null)
}

class RoomBoardRoles(private val dao: BoardAccessDao) : BoardRoles {
    override suspend fun roleOf(boardId: String): WorkspaceRole? = dao.roleOf(boardId)?.let(BoardRoleRules::parseRole)

    override fun observeRole(boardId: String): Flow<WorkspaceRole?> =
        dao.observeRole(boardId).map { raw -> raw?.let(BoardRoleRules::parseRole) }
}
