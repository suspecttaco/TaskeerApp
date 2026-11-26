package com.simposio.taskeer.ui.workspaces

enum class WorkspacesPhase { LOADING, LIST, EMPTY, ERROR }

data class WorkspaceRowUi(
    val id: String,
    val name: String,
    val role: RoleLabel,
    val actions: Set<RowAction>,
)

sealed interface WorkspaceDialog {
    data object None : WorkspaceDialog

    data object Create : WorkspaceDialog

    data class Rename(val id: String, val currentName: String) : WorkspaceDialog

    data class ConfirmDelete(val id: String, val name: String) : WorkspaceDialog
}

data class WorkspacesUiState(
    val phase: WorkspacesPhase = WorkspacesPhase.LOADING,
    val rows: List<WorkspaceRowUi> = emptyList(),
    val refreshing: Boolean = false,
    val error: WorkspaceErrorMessage? = null,
    val dialog: WorkspaceDialog = WorkspaceDialog.None,
    val submitting: Boolean = false,
    val dialogError: WorkspaceErrorMessage? = null,
)

sealed interface WorkspacesEvent {
    data class ShowError(val message: WorkspaceErrorMessage) : WorkspacesEvent

    data object LeaveScreen : WorkspacesEvent
}
