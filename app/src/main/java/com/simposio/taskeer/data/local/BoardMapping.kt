package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.Board
import java.time.Instant

fun BoardEntity.toBoard(): Board = Board(
    name = name,
    createdAt = Instant.ofEpochMilli(createdAt),
    sync = toSyncMetadata(),
)

fun Board.toEntity(): BoardEntity = BoardEntity(
    id = id,
    name = name,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = sync.updatedAt.toEpochMilli(),
    pending = sync.pending,
    deletedAt = sync.deletedAt?.toEpochMilli(),
)
