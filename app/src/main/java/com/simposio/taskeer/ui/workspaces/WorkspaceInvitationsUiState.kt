package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.workspace.InvitationRules
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import java.time.Instant

enum class SectionVisibility { HIDDEN, LOADING, LIST, EMPTY, ERROR }

// Correo, rol y fechas de un tercero: no deben acabar en logs ni mensajes.
data class InvitationRowUi(
    val id: String,
    val email: String,
    val role: RoleLabel,
    val expiresAt: Instant,
    val expired: Boolean,
    val actions: Set<InvitationAction>,
) {
    override fun toString(): String = "InvitationRowUi(redactada, expired=$expired)"
}

sealed interface InvitationDialog {
    data object None : InvitationDialog

    data class Invite(val email: String = "", val role: WorkspaceRole = InvitationRules.DEFAULT_ROLE) : InvitationDialog {
        override fun toString(): String = "Invite(redactado, role=$role)"
    }

    data class VerifyEmail(val email: String = "", val role: WorkspaceRole = InvitationRules.DEFAULT_ROLE) : InvitationDialog {
        override fun toString(): String = "VerifyEmail(redactado, role=$role)"
    }

    data class ConfirmRevoke(val invitationId: String, val email: String) : InvitationDialog {
        override fun toString(): String = "ConfirmRevoke(redactado)"
    }
}

data class WorkspaceInvitationsUiState(
    val section: SectionVisibility = SectionVisibility.HIDDEN,
    val rows: List<InvitationRowUi> = emptyList(),
    val canInvite: Boolean = false,
    val error: WorkspaceInvitationErrorMessage? = null,
    val dialog: InvitationDialog = InvitationDialog.None,
    val submitting: Boolean = false,
    val dialogError: WorkspaceInvitationErrorMessage? = null,
)

sealed interface InvitationNotice {
    data class Invited(val email: String) : InvitationNotice {
        override fun toString(): String = "Invited(redactado)"
    }
}

sealed interface InvitationEvent {
    data class ShowError(val message: WorkspaceInvitationErrorMessage) : InvitationEvent

    data class ShowMessage(val notice: InvitationNotice) : InvitationEvent

    data object OpenVerifyEmail : InvitationEvent

    data object LeaveScreen : InvitationEvent
}

enum class InboxPhase { LOADING, LIST, EMPTY, ERROR }

data class MyInvitationRowUi(
    val id: String,
    val workspaceName: String,
    val role: RoleLabel,
    val expiresAt: Instant,
    val expired: Boolean,
    val canAccept: Boolean,
    val accepting: Boolean,
) {
    override fun toString(): String = "MyInvitationRowUi(redactada, expired=$expired)"
}

enum class InboxDialog { NONE, ACCEPT_WITH_LINK }

data class MyInvitationsUiState(
    val phase: InboxPhase = InboxPhase.LOADING,
    val rows: List<MyInvitationRowUi> = emptyList(),
    val refreshing: Boolean = false,
    val error: WorkspaceInvitationErrorMessage? = null,
    val dialog: InboxDialog = InboxDialog.NONE,
    val submitting: Boolean = false,
    val dialogError: WorkspaceInvitationErrorMessage? = null,
)

sealed interface InboxNotice {
    data class Joined(val workspaceName: String) : InboxNotice {
        override fun toString(): String = "Joined(redactado)"
    }

    data class AlreadyMember(val workspaceName: String) : InboxNotice {
        override fun toString(): String = "AlreadyMember(redactado)"
    }
}

sealed interface InboxEvent {
    data class ShowError(val message: WorkspaceInvitationErrorMessage) : InboxEvent

    data class ShowMessage(val notice: InboxNotice) : InboxEvent

    data object LeaveScreen : InboxEvent
}
