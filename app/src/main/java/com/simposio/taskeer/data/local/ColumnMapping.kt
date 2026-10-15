package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.Column

fun ColumnEntity.toColumn(): Column = Column(
    boardId = boardId,
    name = name,
    position = position,
    wipLimit = wipLimit,
    isDoneColumn = isDoneColumn,
    sync = toSyncMetadata(),
)

fun Column.toEntity(): ColumnEntity = ColumnEntity(
    id = id,
    boardId = boardId,
    name = name,
    position = position,
    wipLimit = wipLimit,
    isDoneColumn = isDoneColumn,
    updatedAt = sync.updatedAt.toEpochMilli(),
    pending = sync.pending,
    deletedAt = sync.deletedAt?.toEpochMilli(),
)
