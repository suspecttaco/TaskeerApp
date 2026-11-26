package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.model.DomainError

sealed interface WorkspaceMemberErrorMessage {
    data object ForbiddenAction : WorkspaceMemberErrorMessage

    data object OwnerRoleImmutable : WorkspaceMemberErrorMessage

    data object OwnerMustTransfer : WorkspaceMemberErrorMessage

    data object TransferToSelf : WorkspaceMemberErrorMessage

    data object NotAMember : WorkspaceMemberErrorMessage

    data object MemberGone : WorkspaceMemberErrorMessage

    data object WorkspaceGone : WorkspaceMemberErrorMessage

    data class RateLimited(val minutes: Int?) : WorkspaceMemberErrorMessage

    data object Offline : WorkspaceMemberErrorMessage

    data object ServerDown : WorkspaceMemberErrorMessage

    data object Generic : WorkspaceMemberErrorMessage
}

object WorkspaceMemberErrorRules {
    const val MEMBER_NOT_FOUND = "MEMBER_NOT_FOUND"
    const val WORKSPACE_NOT_FOUND = "WORKSPACE_NOT_FOUND"
    const val OWNER_MUST_TRANSFER = "OWNER_MUST_TRANSFER_OWNERSHIP"
    const val CANNOT_CHANGE_OWNER_ROLE = "CANNOT_CHANGE_OWNER_ROLE"
    const val CANNOT_TRANSFER_TO_SELF = "CANNOT_TRANSFER_TO_SELF"
    const val USER_NOT_WORKSPACE_MEMBER = "USER_NOT_WORKSPACE_MEMBER"

    // Los tipos agrupan varios códigos: se distingue por `code`. Nunca usa `error.message` (viene del servidor).
    // null = sin mensaje propio: el cierre de sesión se rige por la sesión.
    fun messageFor(error: DomainError, retryAfterSeconds: Long?): WorkspaceMemberErrorMessage? = when (error) {
        is DomainError.Unauthenticated -> null
        is DomainError.Forbidden -> WorkspaceMemberErrorMessage.ForbiddenAction
        is DomainError.Validation -> when (error.code) {
            CANNOT_CHANGE_OWNER_ROLE -> WorkspaceMemberErrorMessage.OwnerRoleImmutable
            OWNER_MUST_TRANSFER -> WorkspaceMemberErrorMessage.OwnerMustTransfer
            CANNOT_TRANSFER_TO_SELF -> WorkspaceMemberErrorMessage.TransferToSelf
            USER_NOT_WORKSPACE_MEMBER -> WorkspaceMemberErrorMessage.NotAMember
            else -> WorkspaceMemberErrorMessage.Generic
        }
        is DomainError.NotFound -> when (error.code) {
            MEMBER_NOT_FOUND -> WorkspaceMemberErrorMessage.MemberGone
            WORKSPACE_NOT_FOUND -> WorkspaceMemberErrorMessage.WorkspaceGone
            else -> WorkspaceMemberErrorMessage.Generic
        }
        is DomainError.RateLimited -> WorkspaceMemberErrorMessage.RateLimited(AuthRules.retryMinutes(retryAfterSeconds))
        is DomainError.Network -> WorkspaceMemberErrorMessage.Offline
        is DomainError.Server -> WorkspaceMemberErrorMessage.ServerDown
        else -> WorkspaceMemberErrorMessage.Generic
    }
}
