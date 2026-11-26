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
) {
    // Nombre, correo y userId son datos personales de terceros: no deben acabar en logs ni mensajes.
    override fun toString(): String = "MemberRowUi(redactada, role=$role, isSelf=$isSelf)"
}

sealed interface MemberDialog {
    data object None : MemberDialog

    data class ChangeRole(val userId: String, val name: String, val options: List<WorkspaceRole>) : MemberDialog {
        override fun toString(): String = "ChangeRole(redactado, options=$options)"
    }

    data class ConfirmRemove(val userId: String, val name: String) : MemberDialog {
        override fun toString(): String = "ConfirmRemove(redactado)"
    }

    data object ConfirmLeave : MemberDialog

    data class ConfirmTransfer(val userId: String, val name: String) : MemberDialog {
        override fun toString(): String = "ConfirmTransfer(redactado)"
    }
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
