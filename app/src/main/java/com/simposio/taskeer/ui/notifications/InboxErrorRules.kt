package com.simposio.taskeer.ui.notifications

import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.model.DomainError

sealed interface InboxErrorMessage {
    data class RateLimited(val minutes: Int?) : InboxErrorMessage

    data object Offline : InboxErrorMessage

    data object ServerDown : InboxErrorMessage

    data object Generic : InboxErrorMessage
}

object InboxErrorRules {

    // Nunca usa `error.message` (viene del servidor). null = sin mensaje propio: el cierre de sesión se rige por la sesión.
    fun messageFor(error: DomainError, retryAfterSeconds: Long?): InboxErrorMessage? = when (error) {
        is DomainError.Unauthenticated -> null
        is DomainError.RateLimited -> InboxErrorMessage.RateLimited(AuthRules.retryMinutes(retryAfterSeconds))
        is DomainError.Network -> InboxErrorMessage.Offline
        is DomainError.Server -> InboxErrorMessage.ServerDown
        else -> InboxErrorMessage.Generic
    }
}
