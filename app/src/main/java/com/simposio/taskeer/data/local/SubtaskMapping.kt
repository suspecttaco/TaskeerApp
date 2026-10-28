package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.Subtask

fun SubtaskEntity.toSubtask(): Subtask = Subtask(
    taskId = taskId,
    title = title,
    completed = completed,
    position = position,
    sync = toSyncMetadata(),
)

fun Subtask.toEntity(): SubtaskEntity = SubtaskEntity(
    id = id,
    taskId = taskId,
    title = title,
    completed = completed,
    position = position,
    updatedAt = sync.updatedAt.toEpochMilli(),
    pending = sync.pending,
    deletedAt = sync.deletedAt?.toEpochMilli(),
)
