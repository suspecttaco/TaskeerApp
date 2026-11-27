package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncOp
import com.simposio.taskeer.domain.task.UNDO_WINDOW
import com.simposio.taskeer.testing.FakeSyncDao
import com.simposio.taskeer.testing.USER_A
import com.simposio.taskeer.testing.syncList
import com.simposio.taskeer.testing.syncSubtask
import com.simposio.taskeer.testing.syncTask
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomSyncLocalStoreTest {

    private val dao = FakeSyncDao()
    private val scope = SyncScopeHolder(setOf(SyncEntity.LIST, SyncEntity.TASK, SyncEntity.SUBTASK))
    private var ids = 0
    private fun store() = RoomSyncLocalStore(dao, scope, newMutationId = { "m${++ids}" })
    private val store = store()
    private val now = Instant.ofEpochMilli(100_000L)

    private suspend fun entries() = dao.queue()

    @Test
    fun collect_unaEntradaPorEntidad() = runTest {
        dao.put(syncList("list-1"))
        dao.put(syncTask("t1"))
        dao.put(syncTask("t2"))
        dao.put(syncSubtask("s1", "t1"))

        store.collect(now)

        assertEquals(
            setOf("list" to "list-1", "task" to "t1", "task" to "t2", "subtask" to "s1"),
            entries().map { it.entity to it.entityId }.toSet(),
        )
        assertEquals(4, entries().size)
    }

    @Test
    fun collect_creaYBorraSinEnviar_cero() = runTest {
        dao.put(syncList("list-1", pending = false))
        dao.put(syncTask("t1"))
        store.collect(now)
        dao.put(syncTask("t1", deletedAt = 5_000L, updatedAt = 5_000L))

        store.collect(now)

        assertEquals(0, entries().size)
        assertEquals(false, dao.tasks.value.getValue("t1").pending)
    }

    @Test
    fun collect_editaSincronizadaYBorra_delete() = runTest {
        dao.put(syncTask("t1", pending = false))
        dao.baseline("task", "t1", 500L)
        dao.put(syncTask("t1", title = "editada", updatedAt = 2_000L))
        store.collect(now)
        dao.put(syncTask("t1", deletedAt = 3_000L, updatedAt = 3_000L))

        store.collect(now)

        assertEquals(listOf(SyncOp.DELETE.name), entries().map { it.op })
    }

    @Test
    fun collect_editaTresVecesUnaAlta_unaSolaEntradaUpsert() = runTest {
        dao.put(syncTask("t1"))
        store.collect(now)
        repeat(3) { dao.put(syncTask("t1", title = "v$it", updatedAt = 2_000L + it)); store.collect(now) }

        assertEquals(listOf(SyncOp.UPSERT.name), entries().map { it.op })
        assertEquals(1, entries().size)
        assertEquals("m1", entries().single().mutationId)
    }

    @Test
    fun collect_idempotente() = runTest {
        dao.put(syncTask("t1"))
        dao.put(syncList("list-1"))

        store.collect(now)
        val first = entries()
        store.collect(now)

        assertEquals(first, entries())
    }

    @Test
    fun collect_soloEntidadesDelAlcanceDeLaFase() = runTest {
        dao.put(syncList("list-1"))
        dao.put(syncTask("t1"))
        dao.put(syncTask("board-task", listId = null, columnId = "c1"))

        store.collect(now)

        assertTrue(entries().all { SyncEntity.fromWire(it.entity) in scope.scope.value })
        assertNull(entries().firstOrNull { it.entityId == "board-task" })
    }

    @Test
    fun eligible_tareaBorradaDentroDeVentana_noElegible() = runTest {
        dao.put(syncTask("t1", pending = false))
        dao.baseline("task", "t1", 500L)
        dao.put(syncTask("t1", deletedAt = 100_000L, updatedAt = 100_000L))
        store.collect(now)

        assertEquals(emptyList<Any>(), store.eligible(now.plus(UNDO_WINDOW).minusMillis(1)))
        assertEquals(1, store.eligible(now.plus(UNDO_WINDOW)).size)
        assertEquals(now.plus(UNDO_WINDOW), store.nextWake(now))
    }

    @Test
    fun eligible_tareaRestauradaEnVentana_sinDelete() = runTest {
        dao.put(syncTask("t1", pending = false))
        dao.baseline("task", "t1", 500L)
        dao.put(syncTask("t1", deletedAt = 100_000L, updatedAt = 100_000L))
        store.collect(now)
        dao.put(syncTask("t1", deletedAt = null, updatedAt = 101_000L))

        store.collect(now.plusSeconds(1))

        assertTrue(entries().none { it.op == SyncOp.DELETE.name })
        assertTrue(store.eligible(now.plusSeconds(10)).none { it.op == SyncOp.DELETE })
    }

    @Test
    fun eligible_ventanaSobreviveAReinicio() = runTest {
        dao.put(syncTask("t1", pending = false))
        dao.baseline("task", "t1", 500L)
        dao.put(syncTask("t1", deletedAt = 100_000L, updatedAt = 100_000L))
        store.collect(now)

        val restarted = store()

        assertEquals(emptyList<Any>(), restarted.eligible(now.plusSeconds(1)))
        assertEquals(1, restarted.eligible(now.plus(UNDO_WINDOW)).size)
    }

    @Test
    fun eligible_subtareaBorradaConSuTareaEnVentana_esperaALaVentana() = runTest {
        dao.put(syncTask("t1", deletedAt = 100_000L, updatedAt = 100_000L))
        dao.baseline("task", "t1", 500L)
        dao.put(syncSubtask("s1", "t1", deletedAt = 100_000L, updatedAt = 100_000L))
        dao.baseline("subtask", "s1", 500L)
        store.collect(now)

        assertEquals(emptyList<Any>(), store.eligible(now.plusSeconds(1)).filter { it.entity == SyncEntity.SUBTASK })
        assertEquals(2, store.eligible(now.plus(UNDO_WINDOW)).size)
    }

    @Test
    fun payload_tareaDeLista_listIdSinColumnId() = runTest {
        dao.put(syncTask("t1", listId = "list-1"))
        store.collect(now)

        val data = payload("t1").data!!

        assertEquals("list-1", data["listId"])
        assertTrue(data.containsKey("columnId"))
        assertNull(data["columnId"])
    }

    @Test
    fun payload_tareaDeTablero_columnIdSinListId() = runTest {
        scope.update(setOf(SyncEntity.BOARD, SyncEntity.COLUMN, SyncEntity.TASK))
        dao.put(syncTask("t1", listId = null, columnId = "c1"))
        store.collect(now)

        val data = payload("t1").data!!

        assertEquals("c1", data["columnId"])
        assertTrue(data.containsKey("listId"))
        assertNull(data["listId"])
    }

    @Test
    fun payload_incluyeRepeat() = runTest {
        dao.put(syncTask("t1", repeatUnit = "WEEK", repeatInterval = 2))
        store.collect(now)

        val data = payload("t1").data!!

        listOf("repeatUnit", "repeatInterval", "repeatWeekdays", "repeatAnchorDay", "repeatSuccessorId").forEach {
            assertTrue("falta $it", data.containsKey(it))
        }
        assertEquals("WEEK", data["repeatUnit"])
        assertEquals(2, data["repeatInterval"])
    }

    @Test
    fun payload_asignadoLocalUser_seMapea() = runTest {
        dao.put(syncTask("t1", assigneeId = LocalUser.ID))
        store.collect(now)

        assertEquals(USER_A, payload("t1").data!!["assigneeId"])
    }

    @Test
    fun payload_completadaYFechas_enIsoUtcConMilisegundos() = runTest {
        dao.put(syncTask("t1", completedAt = 1_764_181_750_000L))
        store.collect(now)

        assertEquals("2025-11-26T18:29:10.000Z", payload("t1").data!!["completedAt"])
    }

    @Test
    fun payload_baseUpdatedAtEsLaVersionDelServidor_yClientUpdatedAtElRelojLocal() = runTest {
        dao.put(syncTask("t1", updatedAt = 7_000L))
        dao.baseline("task", "t1", 1_234L)
        store.collect(now)

        val mutation = payload("t1")

        assertEquals(Instant.ofEpochMilli(1_234L), mutation.baseUpdatedAt)
        assertEquals(Instant.ofEpochMilli(7_000L), mutation.clientUpdatedAt)
    }

    @Test
    fun payload_borradoNoLlevaData() = runTest {
        dao.put(syncTask("t1", pending = false))
        dao.baseline("task", "t1", 1L)
        dao.put(syncTask("t1", deletedAt = 5L, updatedAt = 5L))
        store.collect(now)

        val prepared = store.payloadFor(store.eligible(now.plusSeconds(10)).single(), USER_A)!!

        assertNull(prepared.mutation.data)
        assertEquals(SyncOp.DELETE, prepared.mutation.op)
    }

    @Test
    fun mutationId_estableEntreCiclos() = runTest {
        dao.put(syncTask("t1"))
        store.collect(now)
        val first = store.eligible(now).single().mutationId

        store.collect(now.plusSeconds(60))
        val second = store.eligible(now.plusSeconds(60)).single().mutationId

        assertEquals(first, second)
    }

    @Test
    fun mutationId_cambiaSiLaFilaCambiaTrasUnIntentoFallido() = runTest {
        dao.put(syncTask("t1"))
        store.collect(now)
        val op = store.eligible(now).single()
        store.recordTransientFailure(op, now.plusSeconds(2))
        dao.put(syncTask("t1", title = "otra", updatedAt = 9_000L))

        store.collect(now.plusSeconds(1))

        assertTrue(entries().single().mutationId != op.mutationId)
        assertEquals(0, entries().single().attempts)
    }

    @Test
    fun fueraDeAlcance_noSeEncola_yConservaPending() = runTest {
        scope.update(setOf(SyncEntity.LIST))
        dao.put(syncList("list-1"))
        dao.put(syncTask("t1"))
        dao.put(syncSubtask("s1", "t1"))

        store.collect(now)

        assertEquals(listOf("list-1"), entries().map { it.entityId })
        assertTrue(dao.tasks.value.getValue("t1").pending)
        assertTrue(dao.subtasks.value.getValue("s1").pending)
        assertEquals(1, store.observePendingCount().first())
    }

    @Test
    fun alCrecerElAlcance_seEncola() = runTest {
        scope.update(setOf(SyncEntity.LIST))
        dao.put(syncList("list-1"))
        dao.put(syncTask("t1"))
        store.collect(now)

        scope.update(setOf(SyncEntity.LIST, SyncEntity.TASK))
        store.collect(now)

        assertEquals(setOf("list-1", "t1"), entries().map { it.entityId }.toSet())
        assertEquals(2, store.observePendingCount().first())
    }

    @Test
    fun entradaCuyaFilaYaNoEstaPendiente_seRetira() = runTest {
        dao.put(syncTask("t1"))
        store.collect(now)
        dao.put(syncTask("t1", pending = false))

        store.collect(now)

        assertEquals(0, entries().size)
    }

    @Test
    fun entradaAtascada_noEsElegible() = runTest {
        dao.put(syncTask("t1"))
        store.collect(now)
        val op = store.eligible(now).single()

        repeat(5) { store.recordPermanentFailure(op, "VALIDATION_ERROR") }

        assertEquals(emptyList<Any>(), store.eligible(now))
        assertEquals(1, store.observeStuckCount().first())
    }

    @Test
    fun tareaSinListaNiColumna_noEsEnviable() = runTest {
        dao.put(syncTask("t1", listId = null, columnId = null))

        store.collect(now)

        assertEquals(0, entries().size)
    }

    private fun <T : Any> assertNotNull(value: T?): T = org.junit.Assert.assertNotNull(value).let { value!! }

    private suspend fun payload(taskId: String): PushMutation {
        val op = store.eligible(now.plusSeconds(10), ignoreBackoff = true).first { it.entityId == taskId }
        return assertNotNull(store.payloadFor(op, USER_A)).mutation
    }
}
