package com.simposio.taskeer.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subtasks",
    indices = [Index(value = ["taskId", "position"])],
)
data class SubtaskEntity(
    @PrimaryKey override val id: String,
    val taskId: String,
    val title: String,
    val completed: Boolean,
    val position: Int,
    val assigneeId: String?,
    val dueAt: Long?,
    val createdAt: Long,
    override val updatedAt: Long,
    override val pending: Boolean,
    override val deletedAt: Long?,
) : SyncableEntity
