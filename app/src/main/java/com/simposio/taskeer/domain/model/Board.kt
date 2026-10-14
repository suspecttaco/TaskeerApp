package com.simposio.taskeer.domain.model

import java.time.Instant

data class Board(
    val name: String,
    val createdAt: Instant,
    val sync: SyncMetadata,
) {
    val id: String get() = sync.clientId
    val isDeleted: Boolean get() = sync.isDeleted
}
