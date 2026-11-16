package com.simposio.taskeer.domain.model

import java.time.Instant

data class Task(
    val columnId: String?,
    val title: String,
    val description: String?,
    val priority: TaskPriority,
    val dueAt: Instant?,
    val assigneeId: String?,
    val position: Int,
    val createdAt: Instant,
    val completedAt: Instant?,
    val sync: SyncMetadata,
) {
    val id: String get() = sync.clientId
    val isDeleted: Boolean get() = sync.isDeleted
    val isInbox: Boolean get() = columnId == null
    val isCompleted: Boolean get() = completedAt != null
}
