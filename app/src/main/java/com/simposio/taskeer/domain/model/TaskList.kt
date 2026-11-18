package com.simposio.taskeer.domain.model

import java.time.Instant

data class TaskList(
    val name: String,
    val position: Int,
    val createdAt: Instant,
    val sync: SyncMetadata,
) {
    val id: String get() = sync.clientId
    val isDeleted: Boolean get() = sync.isDeleted
}

data class TaskListSummary(val list: TaskList, val activeCount: Int)
