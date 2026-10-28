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
    override val updatedAt: Long,
    override val pending: Boolean,
    override val deletedAt: Long?,
) : SyncableEntity
