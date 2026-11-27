package com.simposio.taskeer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Rol efectivo que el servidor resolvió para el usuario en un tablero; nunca lo calcula el cliente. La clave primaria
// es el tablero, así que hay como mucho una fila por tablero. Sin fila, el tablero es local y propio.
// Es infraestructura de sincronización, no una entidad sincronizable: no lleva SyncableEntity.
@Entity(tableName = "board_access")
data class BoardAccessEntity(
    @PrimaryKey val boardId: String,
    val workspaceId: String,
    val effectiveRole: String,
    val updatedAt: Long,
)
