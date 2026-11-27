package com.simposio.taskeer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "boards")
data class BoardEntity(
    @PrimaryKey override val id: String,
    val name: String,
    val createdAt: Long,
    override val updatedAt: Long,
    override val pending: Boolean,
    override val deletedAt: Long?,
    // Nulo mientras el tablero es local y aún no se ha subido: al enviarlo va al workspace personal.
    val workspaceId: String? = null,
) : SyncableEntity
