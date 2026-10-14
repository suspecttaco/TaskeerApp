package com.simposio.taskeer.data.local

/** Convención de columnas de toda entidad sincronizable de la base local. */
interface SyncableEntity {
    val id: String
    val updatedAt: Long
    val pending: Boolean
    val deletedAt: Long?
}
