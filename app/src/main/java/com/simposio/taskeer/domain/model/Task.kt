package com.simposio.taskeer.domain.model

import java.time.Instant

data class Task(
    val columnId: String?,
    val listId: String? = null,
    val title: String,
    val description: String?,
    val priority: TaskPriority,
    val dueAt: Instant?,
    val assigneeId: String?,
    val position: Int,
    val createdAt: Instant,
    val completedAt: Instant?,
    val repeat: TaskRepeat? = null,
    val repeatSuccessorId: String? = null,
    val sync: SyncMetadata,
) {
    val id: String get() = sync.clientId
    val isDeleted: Boolean get() = sync.isDeleted
    val isInList: Boolean get() = columnId == null
    val isCompleted: Boolean get() = completedAt != null
}
