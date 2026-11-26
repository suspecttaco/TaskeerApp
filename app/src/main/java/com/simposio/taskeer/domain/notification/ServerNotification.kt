package com.simposio.taskeer.domain.notification

import java.time.Instant

enum class ServerNotificationType {
    REMINDER,
    INVITATION,
    ROLE_CHANGED,
    MENTION,
    RECOVERY_CODE_USED,
    TASK_ASSIGNED,
    UNKNOWN,
}

data class ServerNotification(
    val id: String,
    val type: ServerNotificationType,
    val title: String,
    val body: String?,
    val taskId: String?,
    val createdAt: Instant,
    val readAt: Instant?,
) {
    val isUnread: Boolean get() = readAt == null

    // Título y cuerpo pueden contener nombres de tareas ajenas: no entran en logs ni mensajes.
    override fun toString(): String = "ServerNotification(id=$id, type=$type, unread=$isUnread)"
}
