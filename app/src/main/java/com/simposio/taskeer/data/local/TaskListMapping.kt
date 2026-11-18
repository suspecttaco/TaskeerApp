package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.TaskList
import java.time.Instant

fun TaskListEntity.toTaskList(): TaskList = TaskList(
    name = name,
    position = position,
    createdAt = Instant.ofEpochMilli(createdAt),
    sync = toSyncMetadata(),
)

fun TaskList.toEntity(): TaskListEntity = TaskListEntity(
    id = id,
    name = name,
    position = position,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = sync.updatedAt.toEpochMilli(),
    pending = sync.pending,
    deletedAt = sync.deletedAt?.toEpochMilli(),
)
