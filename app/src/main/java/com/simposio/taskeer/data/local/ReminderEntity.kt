package com.simposio.taskeer.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminders",
    indices = [Index(value = ["status", "remindAt"]), Index(value = ["taskId"])],
)
data class ReminderEntity(
    @PrimaryKey override val id: String,
    val taskId: String,
    val remindAt: Long,
    val status: String,
    val sentAt: Long?,
    val createdAt: Long,
    override val updatedAt: Long,
    override val pending: Boolean,
    // Siempre null hoy: cancelar es DISMISSED, no borrado; la columna existe por la convención.
    override val deletedAt: Long?,
) : SyncableEntity
