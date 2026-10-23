package com.simposio.taskeer.data.local

interface SyncableEntity {
    val id: String
    val updatedAt: Long
    val pending: Boolean
    val deletedAt: Long?
}
