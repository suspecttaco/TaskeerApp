package com.simposio.taskeer.domain.model

import java.time.Instant

enum class AppNotificationKind { DEADLINE, DIGEST, REMINDER }

data class AppNotification(
    val kind: AppNotificationKind,
    val taskId: String?,
    val taskTitle: String?,
    val activeTaskCount: Int?,
    val delivered: Boolean,
    val createdAt: Instant,
    val readAt: Instant?,
    val sync: SyncMetadata,
) {
    val id: String get() = sync.clientId
    val isUnread: Boolean get() = readAt == null
}
