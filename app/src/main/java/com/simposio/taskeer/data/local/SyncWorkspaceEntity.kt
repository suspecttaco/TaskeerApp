package com.simposio.taskeer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Caché ligera de los workspaces que llegan por sincronización: etiqueta tableros y localiza el personal.
// No sustituye a la lista en memoria de la cuenta ni es sincronizable.
@Entity(tableName = "sync_workspaces")
data class SyncWorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isPersonal: Boolean,
    val myRole: String,
    val deletedAt: Long?,
) {
    override fun toString(): String = "SyncWorkspaceEntity(isPersonal=$isPersonal, myRole=$myRole)"
}
