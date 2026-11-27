package com.simposio.taskeer.domain.sync

import java.time.Instant

// Metadatos de una operación pendiente; el contenido de la entidad no vive aquí (se construye al enviar).
data class QueuedOp(
    val mutationId: String,
    val entity: SyncEntity,
    val entityId: String,
    val op: SyncOp,
    val enqueuedAt: Instant,
    val attempts: Int = 0,
    val failures: Int = 0,
    val nextAttemptAt: Instant? = null,
    val lastErrorCode: String? = null,
) {
    override fun toString(): String = "QueuedOp(entity=$entity, op=$op, attempts=$attempts, failures=$failures)"
}
