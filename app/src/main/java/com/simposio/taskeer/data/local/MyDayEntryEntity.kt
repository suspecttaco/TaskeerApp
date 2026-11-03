package com.simposio.taskeer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "my_day_tasks")
data class MyDayEntryEntity(
    // Igual al id de la tarea: una sola fila por tarea, reactivada al volver a marcarla.
    @PrimaryKey override val id: String,
    val addedAt: Long,
    override val updatedAt: Long,
    override val pending: Boolean,
    override val deletedAt: Long?,
) : SyncableEntity
