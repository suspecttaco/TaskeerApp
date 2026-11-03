package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.Column
import java.time.Instant

fun ColumnEntity.toColumn(): Column = Column(
    boardId = boardId,
    name = name,
    position = position,
    wipLimit = wipLimit,
    isDoneColumn = isDoneColumn,
    createdAt = Instant.ofEpochMilli(createdAt),
    sync = toSyncMetadata(),
)

fun Column.toEntity(): ColumnEntity = ColumnEntity(
    id = id,
    boardId = boardId,
    name = name,
    position = position,
    wipLimit = wipLimit,
    isDoneColumn = isDoneColumn,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = sync.updatedAt.toEpochMilli(),
    pending = sync.pending,
    deletedAt = sync.deletedAt?.toEpochMilli(),
)
