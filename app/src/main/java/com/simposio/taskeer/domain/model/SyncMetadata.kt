package com.simposio.taskeer.domain.model

import java.time.Instant
import java.util.UUID

data class SyncMetadata(
    val clientId: String,
    val updatedAt: Instant,
    val pending: Boolean,
    val deletedAt: Instant?,
) {
    val isDeleted: Boolean get() = deletedAt != null
}

fun newClientId(): String = UUID.randomUUID().toString()

fun newSyncMetadata(now: Instant, clientId: String = newClientId()): SyncMetadata = SyncMetadata(
    clientId = clientId,
    updatedAt = now,
    pending = true,
    deletedAt = null,
)

fun SyncMetadata.afterLocalChange(now: Instant): SyncMetadata = copy(updatedAt = now, pending = true)

fun SyncMetadata.afterLocalDelete(now: Instant): SyncMetadata =
    copy(updatedAt = now, pending = true, deletedAt = now)
