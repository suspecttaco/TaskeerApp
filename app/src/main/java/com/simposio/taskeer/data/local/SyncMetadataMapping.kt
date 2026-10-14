package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.SyncMetadata
import java.time.Instant

fun SyncableEntity.toSyncMetadata(): SyncMetadata = SyncMetadata(
    clientId = id,
    updatedAt = Instant.ofEpochMilli(updatedAt),
    pending = pending,
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
)

fun BoardEntity.withSyncMetadata(metadata: SyncMetadata): BoardEntity = copy(
    id = metadata.clientId,
    updatedAt = metadata.updatedAt.toEpochMilli(),
    pending = metadata.pending,
    deletedAt = metadata.deletedAt?.toEpochMilli(),
)
