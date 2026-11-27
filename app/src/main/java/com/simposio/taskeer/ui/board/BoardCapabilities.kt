package com.simposio.taskeer.ui.board

import com.simposio.taskeer.domain.workspace.BoardCapability
import com.simposio.taskeer.domain.workspace.BoardRoleRules
import com.simposio.taskeer.domain.workspace.WorkspaceRole

// Qué ofrece la UI de un tablero según el rol efectivo. Sin rol (tablero local propio) lo ofrece todo; el repositorio
// sigue siendo la barrera real, esto solo evita mostrar acciones que se rechazarían.
data class BoardCapabilities(
    val canEditTasks: Boolean = true,
    val canManageColumns: Boolean = true,
) {
    companion object {
        fun forRole(role: WorkspaceRole?): BoardCapabilities {
            if (role == null) return BoardCapabilities()
            val granted = BoardRoleRules.capabilities(role)
            return BoardCapabilities(
                canEditTasks = BoardCapability.EDIT_TASKS in granted,
                canManageColumns = BoardCapability.MANAGE_COLUMNS in granted,
            )
        }
    }
}
