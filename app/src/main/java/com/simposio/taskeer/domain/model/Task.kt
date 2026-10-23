package com.simposio.taskeer.domain.model

import java.time.Instant

data class Task(
    val columnId: String,
    val title: String,
    val description: String?,
    val priority: TaskPriority,
    val dueAt: Instant?,
    val assigneeId: String?,
    val position: Int,
    val sync: SyncMetadata,
) {
    val id: String get() = sync.clientId
    val isDeleted: Boolean get() = sync.isDeleted
}
