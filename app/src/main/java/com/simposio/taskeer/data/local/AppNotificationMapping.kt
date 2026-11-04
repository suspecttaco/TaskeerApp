package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.AppNotification
import com.simposio.taskeer.domain.model.AppNotificationKind
import java.time.Instant

// Un tipo desconocido (p. ej. de una versión futura) se omite: mostrarlo mal es peor que no mostrarlo.
fun AppNotificationEntity.toAppNotification(): AppNotification? {
    val parsedKind = AppNotificationKind.entries.firstOrNull { it.name == kind } ?: return null
    return AppNotification(
        kind = parsedKind,
        taskId = taskId,
        taskTitle = taskTitle,
        activeTaskCount = activeTaskCount,
        delivered = delivered,
        createdAt = Instant.ofEpochMilli(createdAt),
        readAt = readAt?.let(Instant::ofEpochMilli),
        sync = toSyncMetadata(),
    )
}

fun AppNotification.toEntity(): AppNotificationEntity = AppNotificationEntity(
    id = id,
    kind = kind.name,
    taskId = taskId,
    taskTitle = taskTitle,
    activeTaskCount = activeTaskCount,
    delivered = delivered,
    createdAt = createdAt.toEpochMilli(),
    readAt = readAt?.toEpochMilli(),
    updatedAt = sync.updatedAt.toEpochMilli(),
    pending = sync.pending,
    deletedAt = sync.deletedAt?.toEpochMilli(),
)
