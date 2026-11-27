package com.simposio.taskeer.domain.sync

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncOrderRulesTest {

    private var seq = 0L

    private fun op(entity: SyncEntity, kind: SyncOp, id: String = "${entity.name}-${seq}") = QueuedOp(
        mutationId = "m${seq}",
        entity = entity,
        entityId = id,
        op = kind,
        enqueuedAt = Instant.ofEpochSecond(seq++),
    )

    @Test
    fun respetaDependencias() {
        val ops = listOf(
            op(SyncEntity.SUBTASK, SyncOp.UPSERT),
            op(SyncEntity.TASK, SyncOp.UPSERT),
            op(SyncEntity.LIST, SyncOp.UPSERT),
            op(SyncEntity.REMINDER, SyncOp.UPSERT),
        )

        val order = SyncOrderRules.order(ops).flatten().map { it.entity }

        assertEquals(listOf(SyncEntity.LIST, SyncEntity.TASK, SyncEntity.SUBTASK, SyncEntity.REMINDER), order)
    }

    @Test
    fun borradosDeHijosAntes() {
        val ops = listOf(
            op(SyncEntity.LIST, SyncOp.DELETE),
            op(SyncEntity.TASK, SyncOp.DELETE),
            op(SyncEntity.TASK, SyncOp.UPSERT),
            op(SyncEntity.SUBTASK, SyncOp.DELETE),
        )

        val order = SyncOrderRules.order(ops).flatten().map { it.entity to it.op }

        assertEquals(
            listOf(
                SyncEntity.TASK to SyncOp.UPSERT,
                SyncEntity.SUBTASK to SyncOp.DELETE,
                SyncEntity.TASK to SyncOp.DELETE,
                SyncEntity.LIST to SyncOp.DELETE,
            ),
            order,
        )
    }

    @Test
    fun loteMaximoDeCien() {
        val ops = (1..250).map { op(SyncEntity.TASK, SyncOp.UPSERT) }

        assertEquals(listOf(100, 100, 50), SyncOrderRules.order(ops).map { it.size })
    }
}
