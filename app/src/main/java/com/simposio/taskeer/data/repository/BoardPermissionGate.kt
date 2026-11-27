package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.workspace.BoardRoleRules
import com.simposio.taskeer.domain.workspace.WorkspaceRole

const val BOARD_ROLE_FORBIDS = "BOARD_ROLE_FORBIDS"

// Barrera local del rol efectivo (R23): los repositorios la consultan antes de escribir en un tablero. El servidor sigue
// siendo la autoridad; esto evita cambios que se sabe que rechazará y que el usuario los vea aceptados un momento.
class BoardPermissionGate(private val roles: BoardRoles = UnrestrictedBoardRoles) {

    suspend fun denyTaskChange(boardId: String?): DomainResult.Failure? =
        deny(boardId) { BoardRoleRules.canEditTasks(it) }

    suspend fun denyColumnChange(boardId: String?): DomainResult.Failure? =
        deny(boardId) { BoardRoleRules.canManageColumns(it) }

    private suspend fun deny(
        boardId: String?,
        allowed: (WorkspaceRole) -> Boolean,
    ): DomainResult.Failure? {
        val role = boardId?.let { roles.roleOf(it) } ?: return null
        return if (allowed(role)) null else DomainResult.Failure(forbidden())
    }

    private fun forbidden() = DomainError.Forbidden(BOARD_ROLE_FORBIDS, "Tu rol en este tablero no permite este cambio")
}
