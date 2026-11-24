package com.simposio.taskeer.domain.notification

import com.simposio.taskeer.domain.model.AppNotification
import com.simposio.taskeer.domain.model.AppNotificationKind
import com.simposio.taskeer.domain.model.NotificationEvent
import com.simposio.taskeer.domain.model.afterLocalChange
import com.simposio.taskeer.domain.model.newClientId
import com.simposio.taskeer.domain.model.newSyncMetadata
import java.time.Instant

const val HISTORY_LIMIT = 200

private const val BADGE_MAX = 99

fun NotificationEvent.toNotification(
    delivered: Boolean,
    now: Instant,
    clientId: String = newClientId(),
): AppNotification = when (this) {
    is NotificationEvent.Deadline -> entry(
        AppNotificationKind.DEADLINE, taskId, taskTitle, null, delivered, now, clientId,
    )
    is NotificationEvent.Digest -> entry(
        AppNotificationKind.DIGEST, null, null, activeTaskCount, delivered, now, clientId,
    )
    is NotificationEvent.Manual -> entry(
        AppNotificationKind.REMINDER, taskId, taskTitle, null, delivered, now, clientId, reminderId,
    )
}

fun AppNotification.snoozedUntil(until: Instant, now: Instant): AppNotification = copy(
    snoozedUntil = until,
    readAt = readAt ?: now,
    sync = sync.afterLocalChange(now),
)

fun badgeLabel(unread: Int): String? = when {
    unread <= 0 -> null
    unread > BADGE_MAX -> "$BADGE_MAX+"
    else -> unread.toString()
}

private fun entry(
    kind: AppNotificationKind,
    taskId: String?,
    taskTitle: String?,
    activeTaskCount: Int?,
    delivered: Boolean,
    now: Instant,
    clientId: String,
    reminderId: String? = null,
): AppNotification = AppNotification(
    kind = kind,
    taskId = taskId,
    taskTitle = taskTitle,
    activeTaskCount = activeTaskCount,
    delivered = delivered,
    createdAt = now,
    readAt = null,
    reminderId = reminderId,
    snoozedUntil = null,
    sync = newSyncMetadata(now, clientId),
)
