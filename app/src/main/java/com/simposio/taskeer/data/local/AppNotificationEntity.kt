package com.simposio.taskeer.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_notifications",
    indices = [Index(value = ["deletedAt", "createdAt"]), Index(value = ["deletedAt", "readAt"])],
)
data class AppNotificationEntity(
    @PrimaryKey override val id: String,
    val kind: String,
    val taskId: String?,
    val taskTitle: String?,
    val activeTaskCount: Int?,
    val delivered: Boolean,
    val createdAt: Long,
    val readAt: Long?,
    override val updatedAt: Long,
    override val pending: Boolean,
    override val deletedAt: Long?,
) : SyncableEntity
