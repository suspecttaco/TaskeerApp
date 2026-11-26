package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.workspace.WorkspaceRole

enum class MembersPhase { LOADING, LIST, EMPTY, ERROR }

data class MemberRowUi(
    val userId: String,
    val title: String,
    val email: String,
    val role: RoleLabel,
    val isSelf: Boolean,
    val actions: Set<MemberAction>,
)

sealed interface MemberDialog {
    data object None : MemberDialog

    data class ChangeRole(val userId: String, val name: String, val options: List<WorkspaceRole>) : MemberDialog

    data class ConfirmRemove(val userId: String, val name: String) : MemberDialog

    data object ConfirmLeave : MemberDialog

    data class ConfirmTransfer(val userId: String, val name: String) : MemberDialog
}

data class WorkspaceMembersUiState(
    val phase: MembersPhase = MembersPhase.LOADING,
    val title: String = "",
    val rows: List<MemberRowUi> = emptyList(),
    val ownerHint: Boolean = false,
    val refreshing: Boolean = false,
    val error: WorkspaceMemberErrorMessage? = null,
    val dialog: MemberDialog = MemberDialog.None,
    val submitting: Boolean = false,
)

sealed interface MembersNotice {
    data class Left(val workspaceName: String) : MembersNotice

    data class OwnershipTransferred(val memberName: String) : MembersNotice
}

sealed interface MembersEvent {
    data class ShowError(val message: WorkspaceMemberErrorMessage) : MembersEvent

    data class ShowMessage(val notice: MembersNotice) : MembersEvent

    data object LeaveScreen : MembersEvent
}
