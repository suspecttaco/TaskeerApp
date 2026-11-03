package com.simposio.taskeer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "columns")
data class ColumnEntity(
    @PrimaryKey override val id: String,
    val boardId: String,
    val name: String,
    val position: Int,
    val wipLimit: Int?,
    val isDoneColumn: Boolean,
    val createdAt: Long,
    override val updatedAt: Long,
    override val pending: Boolean,
    override val deletedAt: Long?,
) : SyncableEntity
