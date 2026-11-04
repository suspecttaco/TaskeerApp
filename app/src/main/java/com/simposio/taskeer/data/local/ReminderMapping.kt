package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.Reminder
import com.simposio.taskeer.domain.model.ReminderCandidate
import com.simposio.taskeer.domain.model.ReminderStatus
import java.time.Instant

// Estado desconocido -> DISMISSED: un valor nuevo del servidor nunca debe disparar un aviso.
fun ReminderEntity.toReminder(): Reminder = Reminder(
    taskId = taskId,
    remindAt = Instant.ofEpochMilli(remindAt),
    status = ReminderStatus.entries.firstOrNull { it.name == status } ?: ReminderStatus.DISMISSED,
    sentAt = sentAt?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAt),
    sync = toSyncMetadata(),
)

fun Reminder.toEntity(): ReminderEntity = ReminderEntity(
    id = id,
    taskId = taskId,
    remindAt = remindAt.toEpochMilli(),
    status = status.name,
    sentAt = sentAt?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = sync.updatedAt.toEpochMilli(),
    pending = sync.pending,
    deletedAt = sync.deletedAt?.toEpochMilli(),
)

fun ReminderCandidateRow.toCandidate(): ReminderCandidate = ReminderCandidate(
    taskId = id,
    title = title,
    dueAt = Instant.ofEpochMilli(dueAt),
)
