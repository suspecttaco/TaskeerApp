package com.simposio.taskeer.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [Index(value = ["columnId", "position"])],
)
data class TaskEntity(
    @PrimaryKey override val id: String,
    val columnId: String,
    val title: String,
    val description: String?,
    val priority: String,
    val dueAt: Long?,
    val assigneeId: String?,
    val position: Int,
    override val updatedAt: Long,
    override val pending: Boolean,
    override val deletedAt: Long?,
) : SyncableEntity
