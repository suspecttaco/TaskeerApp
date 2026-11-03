package com.simposio.taskeer.domain.model

import java.time.Instant

data class Subtask(
    val taskId: String,
    val title: String,
    val completed: Boolean,
    val position: Int,
    val assigneeId: String? = null,
    val dueAt: Instant? = null,
    val createdAt: Instant,
    val sync: SyncMetadata,
) {
    val id: String get() = sync.clientId
    val isDeleted: Boolean get() = sync.isDeleted
}
