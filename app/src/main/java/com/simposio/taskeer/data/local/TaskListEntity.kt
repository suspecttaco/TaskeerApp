package com.simposio.taskeer.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_lists",
    indices = [Index(value = ["deletedAt", "position"])],
)
data class TaskListEntity(
    @PrimaryKey override val id: String,
    val name: String,
    val position: Int,
    val createdAt: Long,
    override val updatedAt: Long,
    override val pending: Boolean,
    override val deletedAt: Long?,
) : SyncableEntity
