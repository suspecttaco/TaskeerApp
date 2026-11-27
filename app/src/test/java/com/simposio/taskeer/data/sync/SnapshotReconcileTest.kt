package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.testing.SyncRig
import com.simposio.taskeer.testing.at
import com.simposio.taskeer.testing.syncList
import com.simposio.taskeer.testing.syncTask
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotReconcileTest {

    private val rig = SyncRig()
    private val dao get() = rig.dao
    private val backend get() = rig.backend

    private suspend fun TestScope.cycle() = with(rig) { engine().runCycle(manual = true) }

    @Test
    fun snapshotSinTareaSincronizada_laBorra() = runTest {
        rig.bound()
        rig.syncedTask("vieja")
        rig.seedTask("viva", "sigue en el servidor", at("2026-11-26T10:00:00Z"))

        cycle()

        assertEquals(rig.now.toEpochMilli(), dao.tasks.value.getValue("vieja").deletedAt)
        assertFalse(dao.tasks.value.getValue("vieja").pending)
        assertNull(dao.tasks.value.getValue("viva").deletedAt)
    }

    @Test
    fun snapshotSinTareaNuncaEnviada_laConservaYEncola() = runTest {
        rig.bound()
        dao.put(syncTask("nunca", listId = rig.listId, pending = false))

        cycle()

        val row = dao.tasks.value.getValue("nunca")
        assertNull(row.deletedAt)
        assertTrue(row.pending)

        cycle()

        assertNotNull(backend.task("nunca"))
        assertFalse(dao.tasks.value.getValue("nunca").pending)
    }

    @Test
    fun cursorInvalido_recargaCompleta() = runTest {
        rig.bound()
        val stale = "i:5:0:viejo"
        dao.metas.value = dao.metas.value + (SyncMetaKeys.CURSOR to stale)
        backend.invalidCursors += stale
        rig.syncedTask("ausente")

        cycle()

        assertEquals(listOf(stale, null), backend.pulls.map { it.first })
        assertNotNull(dao.tasks.value.getValue("ausente").deletedAt)
        assertNotEquals(stale, dao.metas.value[SyncMetaKeys.CURSOR])
    }

    @Test
    fun snapshotCortadoAMitad_noBorraNada() = runTest {
        rig.bound()
        (1..3).forEach { rig.seedTask("t$it", "tarea $it", at("2026-11-26T10:0$it:00Z")) }
        backend.pageCap = 2
        backend.failPullCalls += 2
        rig.syncedTask("ausente")

        cycle()

        assertNull(dao.tasks.value.getValue("ausente").deletedAt)
        assertNull(dao.metas.value[SyncMetaKeys.CURSOR])
    }

    @Test
    fun snapshotCompleto_guardaCursorAlFinal() = runTest {
        rig.bound()
        (1..3).forEach { rig.seedTask("t$it", "tarea $it", at("2026-11-26T10:0$it:00Z")) }
        backend.pageCap = 2
        backend.failPullCalls += 2
        cycle()
        assertNull(dao.metas.value[SyncMetaKeys.CURSOR])

        cycle()

        assertEquals("i:${at("2026-11-26T10:03:00Z").toEpochMilli()}:3:t3", dao.metas.value[SyncMetaKeys.CURSOR])
        assertEquals(setOf("t1", "t2", "t3"), dao.tasks.value.keys)
    }

    @Test
    fun snapshotForzadoPorServidor_seTrataComoCompleto() = runTest {
        rig.bound()
        dao.metas.value = dao.metas.value + (SyncMetaKeys.CURSOR to "i:0:0:")
        backend.forceSnapshotNext = true
        rig.syncedTask("ausente")

        cycle()

        assertNotNull(dao.tasks.value.getValue("ausente").deletedAt)
        assertEquals("i:0:0:", backend.pulls.first().first)
    }

    @Test
    fun ausenciaSoloDeEntidadesEnAlcance() = runTest {
        rig.bound()
        backend.supported = setOf(SyncEntity.LIST)
        dao.put(syncList("lista-ausente", pending = false, updatedAt = 5L))
        dao.baseline("list", "lista-ausente", 5L)
        rig.syncedTask("tarea-de-lista")
        rig.syncedTask("tarea-de-tablero", columnId = "col-1")

        cycle()

        assertNotNull(dao.lists.value.getValue("lista-ausente").deletedAt)
        assertNull(dao.tasks.value.getValue("tarea-de-lista").deletedAt)
        assertNull(dao.tasks.value.getValue("tarea-de-tablero").deletedAt)
    }

    @Test
    fun subtareasFueraDeAlcance_noSeBorran() = runTest {
        rig.bound()
        backend.supported = setOf(SyncEntity.LIST, SyncEntity.TASK)
        rig.seedTask("t1", "viva", at("2026-11-26T10:00:00Z"))
        rig.syncedTask("t1")
        rig.syncedSubtask("s1", "t1")
        rig.syncedTask("t2")

        cycle()

        assertNull(dao.subtasks.value.getValue("s1").deletedAt)
        assertNull(dao.tasks.value.getValue("t1").deletedAt)
        assertNotNull(dao.tasks.value.getValue("t2").deletedAt)
    }
}
