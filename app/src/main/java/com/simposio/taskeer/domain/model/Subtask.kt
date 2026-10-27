package com.simposio.taskeer.domain.model

data class Subtask(
    val taskId: String,
    val title: String,
    val completed: Boolean,
    val position: Int,
    val sync: SyncMetadata,
) {
    val id: String get() = sync.clientId
    val isDeleted: Boolean get() = sync.isDeleted
}
