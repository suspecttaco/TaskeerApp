package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.Subtask
import java.time.Instant

fun SubtaskEntity.toSubtask(): Subtask = Subtask(
    taskId = taskId,
    title = title,
    completed = completed,
    position = position,
    assigneeId = assigneeId,
    dueAt = dueAt?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAt),
    sync = toSyncMetadata(),
)

fun Subtask.toEntity(): SubtaskEntity = SubtaskEntity(
    id = id,
    taskId = taskId,
    title = title,
    completed = completed,
    position = position,
    assigneeId = assigneeId,
    dueAt = dueAt?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = sync.updatedAt.toEpochMilli(),
    pending = sync.pending,
    deletedAt = sync.deletedAt?.toEpochMilli(),
)
