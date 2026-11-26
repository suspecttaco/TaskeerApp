package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.model.DomainError

enum class WorkspaceAction { LOAD, CREATE, RENAME, DELETE }

sealed interface WorkspaceErrorMessage {
    data object PlanLimit : WorkspaceErrorMessage

    data object OnlyOwnerCanDelete : WorkspaceErrorMessage

    data object NoPermission : WorkspaceErrorMessage

    data object EmailNotVerified : WorkspaceErrorMessage

    data object InvalidName : WorkspaceErrorMessage

    data object CheckName : WorkspaceErrorMessage

    data object Unavailable : WorkspaceErrorMessage

    data class RateLimited(val minutes: Int?) : WorkspaceErrorMessage

    data object Offline : WorkspaceErrorMessage

    data object ServerDown : WorkspaceErrorMessage

    data object Generic : WorkspaceErrorMessage
}

object WorkspaceErrorRules {
    private const val LIMIT_CODE = "WORKSPACE_LIMIT_REACHED"
    private const val EMAIL_NOT_VERIFIED_CODE = "EMAIL_NOT_VERIFIED"

    // El tipo Forbidden agrupa permisos, plan y correo: se distingue por `code`. Nunca usa `error.message` (viene del servidor).
    // null = sin mensaje propio: el cierre de sesión se rige por la sesión.
    fun messageFor(error: DomainError, retryAfterSeconds: Long?, action: WorkspaceAction): WorkspaceErrorMessage? = when (error) {
        is DomainError.Unauthenticated -> null
        is DomainError.Forbidden -> when {
            error.code == LIMIT_CODE -> WorkspaceErrorMessage.PlanLimit
            error.code == EMAIL_NOT_VERIFIED_CODE -> WorkspaceErrorMessage.EmailNotVerified
            action == WorkspaceAction.DELETE -> WorkspaceErrorMessage.OnlyOwnerCanDelete
            else -> WorkspaceErrorMessage.NoPermission
        }
        is DomainError.Validation -> WorkspaceErrorMessage.CheckName
        is DomainError.NotFound -> WorkspaceErrorMessage.Unavailable
        is DomainError.RateLimited -> WorkspaceErrorMessage.RateLimited(AuthRules.retryMinutes(retryAfterSeconds))
        is DomainError.Network -> WorkspaceErrorMessage.Offline
        is DomainError.Server -> WorkspaceErrorMessage.ServerDown
        else -> WorkspaceErrorMessage.Generic
    }
}
