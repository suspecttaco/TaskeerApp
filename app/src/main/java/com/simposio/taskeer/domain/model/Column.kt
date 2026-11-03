package com.simposio.taskeer.domain.model

import java.time.Instant

data class Column(
    val boardId: String,
    val name: String,
    val position: Int,
    val wipLimit: Int?,
    val isDoneColumn: Boolean,
    val createdAt: Instant,
    val sync: SyncMetadata,
) {
    val id: String get() = sync.clientId
    val isDeleted: Boolean get() = sync.isDeleted
}
