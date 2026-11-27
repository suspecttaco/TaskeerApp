package com.simposio.taskeer.domain.sync

object SyncOrderRules {
    const val MAX_BATCH: Int = 100

    // Altas y ediciones de padres a hijos; después los borrados de hijos a padres (contrato 1.0, sección 5.6).
    fun order(ops: List<QueuedOp>): List<List<QueuedOp>> {
        val upserts = ops.filter { it.op == SyncOp.UPSERT }
            .sortedWith(compareBy({ it.entity.ordinal }, { it.enqueuedAt }, { it.entityId }))
        val deletes = ops.filter { it.op == SyncOp.DELETE }
            .sortedWith(compareBy({ -it.entity.ordinal }, { it.enqueuedAt }, { it.entityId }))
        return (upserts + deletes).chunked(MAX_BATCH)
    }
}
