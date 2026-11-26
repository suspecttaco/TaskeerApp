package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.workspace.BoardCapability
import com.simposio.taskeer.domain.workspace.WorkspaceRole

enum class BoardAccessPhase { LOADING, READY, ERROR }

data class OverrideRowUi(
    val userId: String,
    val title: String,
    val email: String,
    val workspaceRole: RoleLabel,
    val boardRole: BoardRoleDisplay,
    val actions: Set<OverrideAction>,
) {
    // Nombre, correo y userId son datos personales de terceros: no deben acabar en logs ni mensajes.
    override fun toString(): String = "OverrideRowUi(redactada, workspaceRole=$workspaceRole, boardRole=$boardRole)"
}

sealed interface BoardDialog {
    data object None : BoardDialog

    data class SetRole(val userId: String, val name: String, val options: List<WorkspaceRole>) : BoardDialog {
        override fun toString(): String = "SetRole(redactado, options=$options)"
    }

    data class ConfirmAdmin(val userId: String, val name: String) : BoardDialog {
        override fun toString(): String = "ConfirmAdmin(redactado)"
    }

    data class ConfirmRemove(val userId: String, val name: String, val boardName: String) : BoardDialog {
        override fun toString(): String = "ConfirmRemove(redactado)"
    }
}

data class BoardAccessUiState(
    val phase: BoardAccessPhase = BoardAccessPhase.LOADING,
    val title: String = "",
    val roleLabel: RoleLabel? = null,
    val capabilities: List<BoardCapability> = emptyList(),
    val showPanel: Boolean = false,
    val rows: List<OverrideRowUi> = emptyList(),
    val panelLoading: Boolean = false,
    val panelError: Boolean = false,
    val noOtherMembers: Boolean = false,
    val refreshing: Boolean = false,
    val error: BoardRoleErrorMessage? = null,
    val dialog: BoardDialog = BoardDialog.None,
    val submitting: Boolean = false,
) {
    // El título es el nombre del tablero: dato de usuario que no debe acabar en logs.
    override fun toString(): String =
        "BoardAccessUiState(phase=$phase, roleLabel=$roleLabel, showPanel=$showPanel, rows=${rows.size}, submitting=$submitting)"
}

sealed interface BoardAccessNotice {
    data class RoleChanged(val role: WorkspaceRole) : BoardAccessNotice
}

sealed interface BoardAccessEvent {
    data class ShowError(val message: BoardRoleErrorMessage) : BoardAccessEvent

    data class ShowMessage(val notice: BoardAccessNotice) : BoardAccessEvent

    data object LeaveScreen : BoardAccessEvent
}
