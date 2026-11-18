package com.simposio.taskeer.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [Index(value = ["columnId", "position"]), Index(value = ["listId"])],
)
data class TaskEntity(
    @PrimaryKey override val id: String,
    val columnId: String?,
    val title: String,
    val description: String?,
    val priority: String,
    val dueAt: Long?,
    val assigneeId: String?,
    val position: Int,
    val createdAt: Long,
    val completedAt: Long?,
    override val updatedAt: Long,
    override val pending: Boolean,
    override val deletedAt: Long?,
    val repeatUnit: String? = null,
    val repeatInterval: Int? = null,
    // Máscara de 7 bits, bit 0 = lunes; NULL o 0 significa sin días elegidos.
    val repeatWeekdays: Int? = null,
    val repeatAnchorDay: Int? = null,
    // Sin clave foránea: la sucesora puede borrarse sin afectar a la original.
    val repeatSuccessorId: String? = null,
    // Sin clave foránea, como columnId; exclusivo con columnId (una tarea está en lista o en columna).
    val listId: String? = null,
) : SyncableEntity
