package com.simposio.taskeer.data.local

import androidx.room.Entity

// Último updatedAt del servidor conocido para cada entidad: es el baseUpdatedAt del envío.
@Entity(tableName = "sync_baseline", primaryKeys = ["entity", "entityId"])
data class SyncBaselineEntity(
    val entity: String,
    val entityId: String,
    val serverUpdatedAt: Long,
)
