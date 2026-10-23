package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import java.time.Instant

fun TaskEntity.toTask(): Task = Task(
    columnId = columnId,
    title = title,
    description = description,
    priority = TaskPriority.fromStorage(priority),
    dueAt = dueAt?.let(Instant::ofEpochMilli),
    assigneeId = assigneeId,
    position = position,
    sync = toSyncMetadata(),
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    columnId = columnId,
    title = title,
    description = description,
    priority = priority.name,
    dueAt = dueAt?.toEpochMilli(),
    assigneeId = assigneeId,
    position = position,
    updatedAt = sync.updatedAt.toEpochMilli(),
    pending = sync.pending,
    deletedAt = sync.deletedAt?.toEpochMilli(),
)
