package com.simposio.taskeer.domain.notification

import com.simposio.taskeer.domain.model.DomainError

data class InboxState(
    val items: List<ServerNotification> = emptyList(),
    val unreadCount: Int = 0,
    // La lista completa ya se pidió con éxito en esta sesión; sin ella `items` está vacío y el contador viene del servidor.
    val listLoaded: Boolean = false,
)

sealed interface InboxOutcome {
    data object Done : InboxOutcome

    data class Failed(val error: DomainError, val retryAfterSeconds: Long? = null) : InboxOutcome
}
