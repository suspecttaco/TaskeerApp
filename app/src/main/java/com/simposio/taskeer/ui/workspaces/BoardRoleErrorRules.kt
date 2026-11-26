package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.model.DomainError

sealed interface BoardRoleErrorMessage {
    data object ForbiddenAction : BoardRoleErrorMessage

    data object BoardGone : BoardRoleErrorMessage

    data object NotAMember : BoardRoleErrorMessage

    data class RateLimited(val minutes: Int?) : BoardRoleErrorMessage

    data object Offline : BoardRoleErrorMessage

    data object ServerDown : BoardRoleErrorMessage

    data object Generic : BoardRoleErrorMessage
}

object BoardRoleErrorRules {
    const val BOARD_NOT_FOUND = "BOARD_NOT_FOUND"
    const val WORKSPACE_NOT_FOUND = "WORKSPACE_NOT_FOUND"
    const val USER_NOT_WORKSPACE_MEMBER = "USER_NOT_WORKSPACE_MEMBER"

    // Los tipos agrupan varios códigos: se distingue por `code`. Nunca usa `error.message` (viene del servidor).
    // null = sin mensaje propio: el cierre de sesión se rige por la sesión.
    fun messageFor(error: DomainError, retryAfterSeconds: Long?): BoardRoleErrorMessage? = when (error) {
        is DomainError.Unauthenticated -> null
        is DomainError.Forbidden -> BoardRoleErrorMessage.ForbiddenAction
        is DomainError.Validation ->
            if (error.code == USER_NOT_WORKSPACE_MEMBER) BoardRoleErrorMessage.NotAMember else BoardRoleErrorMessage.Generic
        is DomainError.NotFound ->
            if (error.code == BOARD_NOT_FOUND || error.code == WORKSPACE_NOT_FOUND) {
                BoardRoleErrorMessage.BoardGone
            } else {
                BoardRoleErrorMessage.Generic
            }
        is DomainError.RateLimited -> BoardRoleErrorMessage.RateLimited(AuthRules.retryMinutes(retryAfterSeconds))
        is DomainError.Network -> BoardRoleErrorMessage.Offline
        is DomainError.Server -> BoardRoleErrorMessage.ServerDown
        else -> BoardRoleErrorMessage.Generic
    }
}
