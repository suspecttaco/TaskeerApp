package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.model.DomainError

sealed interface WorkspaceInvitationErrorMessage {
    data object EmailNotVerified : WorkspaceInvitationErrorMessage

    data object NoPermission : WorkspaceInvitationErrorMessage

    data object InvalidInvitation : WorkspaceInvitationErrorMessage

    data object WorkspaceGone : WorkspaceInvitationErrorMessage

    data object CheckEmailAndRole : WorkspaceInvitationErrorMessage

    data object InvalidEmail : WorkspaceInvitationErrorMessage

    data object AlreadyMember : WorkspaceInvitationErrorMessage

    data object AlreadyInvited : WorkspaceInvitationErrorMessage

    data object InvalidLink : WorkspaceInvitationErrorMessage

    data class RateLimited(val minutes: Int?) : WorkspaceInvitationErrorMessage

    data object Offline : WorkspaceInvitationErrorMessage

    data object ServerDown : WorkspaceInvitationErrorMessage

    data object Generic : WorkspaceInvitationErrorMessage
}

object WorkspaceInvitationErrorRules {
    const val EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED"
    const val FORBIDDEN = "FORBIDDEN"
    const val INVALID_OR_EXPIRED_INVITATION = "INVALID_OR_EXPIRED_INVITATION"
    const val VALIDATION_ERROR = "VALIDATION_ERROR"
    const val WORKSPACE_NOT_FOUND = "WORKSPACE_NOT_FOUND"

    // Los tipos agrupan varios códigos: se distingue por `code`. Nunca usa `error.message` (viene del servidor).
    // null = sin mensaje propio: el cierre de sesión se rige por la sesión.
    fun messageFor(error: DomainError, retryAfterSeconds: Long?): WorkspaceInvitationErrorMessage? = when (error) {
        is DomainError.Unauthenticated -> null
        is DomainError.Forbidden -> when (error.code) {
            EMAIL_NOT_VERIFIED -> WorkspaceInvitationErrorMessage.EmailNotVerified
            FORBIDDEN -> WorkspaceInvitationErrorMessage.NoPermission
            else -> WorkspaceInvitationErrorMessage.Generic
        }
        is DomainError.Validation -> when (error.code) {
            INVALID_OR_EXPIRED_INVITATION -> WorkspaceInvitationErrorMessage.InvalidInvitation
            VALIDATION_ERROR -> WorkspaceInvitationErrorMessage.CheckEmailAndRole
            else -> WorkspaceInvitationErrorMessage.Generic
        }
        is DomainError.NotFound ->
            if (error.code == WORKSPACE_NOT_FOUND) WorkspaceInvitationErrorMessage.WorkspaceGone else WorkspaceInvitationErrorMessage.InvalidInvitation
        is DomainError.RateLimited -> WorkspaceInvitationErrorMessage.RateLimited(AuthRules.retryMinutes(retryAfterSeconds))
        is DomainError.Network -> WorkspaceInvitationErrorMessage.Offline
        is DomainError.Server -> WorkspaceInvitationErrorMessage.ServerDown
        else -> WorkspaceInvitationErrorMessage.Generic
    }
}
