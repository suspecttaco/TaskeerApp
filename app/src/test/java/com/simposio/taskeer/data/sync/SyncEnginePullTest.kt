package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.testing.SyncRig
import com.simposio.taskeer.testing.USER_A
import com.simposio.taskeer.testing.USER_B
import com.simposio.taskeer.testing.at
import com.simposio.taskeer.testing.syncTask
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SyncEnginePullTest {

    private val rig = SyncRig()
    private val dao get() = rig.dao
    private val backend get() = rig.backend
    private val firstCursor = "i:0:0:"

    private fun incrementalWithFourTasks() {
        rig.bound()
        dao.metas.value = dao.metas.value + (SyncMetaKeys.CURSOR to firstCursor)
        (1..4).forEach { rig.seedTask("t$it", "tarea $it", at("2026-11-26T10:0$it:00Z")) }
        backend.pageCap = 2
    }

    @Test
    fun tresPaginas_aplicaYGuardaCursor() = runTest {
        incrementalWithFourTasks()
        backend.seed(SyncEntity.LIST, "otra", mapOf("name" to "Otra", "position" to 1), at("2026-11-26T09:00:00Z"))

        with(rig) { engine().runCycle(manual = true) }

        assertEquals(3, backend.pullCalls)
        assertEquals(setOf("t1", "t2", "t3", "t4"), dao.tasks.value.keys)
        assertTrue(dao.tasks.value.values.none { it.pending })
        assertNotNull(dao.lists.value["otra"])
        assertEquals(listOf(firstCursor), backend.pulls.take(1).map { it.first })
        assertEquals("i:${at("2026-11-26T10:04:00Z").toEpochMilli()}:5:t4", dao.metas.value[SyncMetaKeys.CURSOR])
    }

    @Test
    fun cortaTrasLaSegunda_reanudaEnLaTercera() = runTest {
        incrementalWithFourTasks()
        backend.failPullCalls += 3

        with(rig) { engine().runCycle(manual = true) }

        val afterSecond = dao.metas.value.getValue(SyncMetaKeys.CURSOR)
        assertNotEquals(firstCursor, afterSecond)
        assertEquals(setOf("t1", "t2", "t3"), dao.tasks.value.keys)

        with(rig) { engine().runCycle(manual = true) }

        assertEquals(afterSecond, backend.pulls[3].first)
        assertEquals(setOf("t1", "t2", "t3", "t4"), dao.tasks.value.keys)
    }

    @Test
    fun mismaPaginaDosVeces_mismoEstado() = runTest {
        rig.bound()
        val page = rig.page(
            listOf(rig.taskRecord("t1", "uno", at("2026-11-26T10:00:00Z")), rig.taskRecord("t2", "dos", at("2026-11-26T10:01:00Z"))),
        )

        rig.store.applyPage(page, USER_A, rig.now, PageCommit.SaveCursor)
        val tasks = dao.tasks.value
        val baselines = dao.baselines.value
        val effect = rig.store.applyPage(page, USER_A, rig.now, PageCommit.SaveCursor)

        assertEquals(tasks, dao.tasks.value)
        assertEquals(baselines, dao.baselines.value)
        assertTrue(effect.losses.isEmpty())
    }

    @Test
    fun tombstone_marcaBorradoSinFisico() = runTest {
        rig.bound()
        dao.put(syncTask("t1", listId = rig.listId, pending = false, updatedAt = 5L))
        dao.baseline("task", "t1", 5L)
        val deletedAt = at("2026-11-26T12:00:00Z")

        rig.store.applyPage(rig.page(listOf(rig.taskRecord("t1", "x", deletedAt, deletedAt))), USER_A, rig.now, PageCommit.SaveCursor)

        val row = dao.tasks.value.getValue("t1")
        assertEquals(deletedAt.toEpochMilli(), row.deletedAt)
        assertFalse(row.pending)
    }

    @Test
    fun versionVieja_noRevivefilaBorrada() = runTest {
        rig.bound()
        dao.put(syncTask("t1", listId = rig.listId, pending = false, updatedAt = 5L))
        dao.baseline("task", "t1", 5L)
        val deletedAt = at("2026-11-26T12:00:00Z")
        val tombstone = rig.page(listOf(rig.taskRecord("t1", "x", deletedAt, deletedAt)))
        rig.store.applyPage(tombstone, USER_A, rig.now, PageCommit.SaveCursor)

        val older = rig.page(listOf(rig.taskRecord("t1", "versión anterior", at("2026-11-26T11:00:00Z"))))
        rig.store.applyPage(older, USER_A, rig.now, PageCommit.SaveCursor)

        assertEquals(deletedAt.toEpochMilli(), dao.tasks.value.getValue("t1").deletedAt)
        assertEquals("x", dao.tasks.value.getValue("t1").title)
    }

    // El DAO falso no ejecuta SQL: «se muestra» se evalúa con la misma regla de VISIBLE_ANY (lista viva con ese id),
    // cuyo texto fija MyDayDaoConventionsTest.consultas_incluyenTareasSinColumnaDeCualquierLista.
    private fun shown(taskId: String): Boolean {
        val task = dao.tasks.value.getValue(taskId)
        return task.deletedAt == null && (task.listId == null || dao.lists.value[task.listId]?.deletedAt == null && dao.lists.value.containsKey(task.listId))
    }

    private fun orphanTask() = rig.taskRecord("t9", "huérfana", at("2026-11-26T10:00:00Z")).let {
        ServerRecord(it.entity, it.id, it.updatedAt, null, it.fields + ("listId" to "lista-tardia"))
    }

    @Test
    fun hijoSinPadre_noSeMuestra() = runTest {
        rig.bound()
        val orphanSubtask = ServerRecord(
            SyncEntity.SUBTASK, "s9", at("2026-11-26T10:00:00Z"), null, mapOf("taskId" to "tarea-que-no-existe", "title" to "hija"),
        )

        rig.store.applyPage(rig.page(listOf(orphanTask(), orphanSubtask)), USER_A, rig.now, PageCommit.SaveCursor)

        assertFalse(dao.tasks.value.getValue("t9").pending)
        assertFalse(dao.subtasks.value.getValue("s9").pending)
        assertFalse(shown("t9"))
    }

    @Test
    fun hijoSinPadre_reapareceAlLlegarLaLista() = runTest {
        rig.bound()
        rig.store.applyPage(rig.page(listOf(orphanTask())), USER_A, rig.now, PageCommit.SaveCursor)
        assertFalse(shown("t9"))

        val list = ServerRecord(SyncEntity.LIST, "lista-tardia", at("2026-11-26T11:00:00Z"), null, mapOf("name" to "Tardía", "position" to 1))
        rig.store.applyPage(rig.page(listOf(list)), USER_A, rig.now, PageCommit.SaveCursor)

        assertTrue(shown("t9"))
    }

    @Test
    fun fallaAMitadDeLaPagina_noDejaFilasNiCursorNuevo() = runTest {
        rig.bound()
        dao.metas.value = dao.metas.value + (SyncMetaKeys.CURSOR to "i:0:0:")
        val lists = dao.lists.value
        val baselines = dao.baselines.value
        val metas = dao.metas.value
        dao.failTaskUpsertAt = 2
        val page = rig.page(
            listOf(
                ServerRecord(SyncEntity.LIST, "nueva", at("2026-11-26T09:00:00Z"), null, mapOf("name" to "Nueva", "position" to 1)),
                rig.taskRecord("t1", "uno", at("2026-11-26T10:00:00Z")),
                rig.taskRecord("t2", "dos", at("2026-11-26T10:01:00Z")),
            ),
            next = "i:99:0:",
        )

        try {
            rig.store.applyPage(page, USER_A, rig.now, PageCommit.SaveCursor)
            fail("debía fallar a mitad de la página")
        } catch (_: IllegalStateException) {
        }

        assertTrue(dao.tasks.value.isEmpty())
        assertEquals(lists, dao.lists.value)
        assertEquals(baselines, dao.baselines.value)
        assertEquals(metas, dao.metas.value)

        dao.failTaskUpsertAt = null
        rig.store.applyPage(page, USER_A, rig.now, PageCommit.SaveCursor)

        assertEquals(setOf("t1", "t2"), dao.tasks.value.keys)
        assertEquals("i:99:0:", dao.metas.value[SyncMetaKeys.CURSOR])
    }

    @Test
    fun entidadDesconocida_seIgnora() = runTest {
        rig.bound()
        val future = ServerRecord(SyncEntity.MY_DAY, "t1", at("2026-11-26T10:00:00Z"), null, mapOf("taskId" to "t1", "addedAt" to "2026-11-26T09:00:00.000Z"))
        val board = ServerRecord(SyncEntity.BOARD, "b1", at("2026-11-26T10:00:00Z"), null, mapOf("name" to "Tablero"))

        val effect = rig.store.applyPage(rig.page(listOf(future, board)), USER_A, rig.now, PageCommit.SaveCursor)

        assertTrue(effect.losses.isEmpty())
        assertTrue(dao.tasks.value.isEmpty())
        assertNull(dao.baseline("board", "b1"))
        assertNull(dao.baseline("myDay", "t1"))
    }

    @Test
    fun soloUnaTransaccionPorPagina() = runTest {
        incrementalWithFourTasks()

        with(rig) { engine().runCycle(manual = true) }

        assertEquals(backend.pullCalls, dao.transactions)
    }

    @Test
    fun pendienteIntacto_siLaVersionYaEraConocida() = runTest {
        rig.bound()
        dao.put(syncTask("t1", listId = rig.listId, updatedAt = 3_000L))
        rig.backend.seed(SyncEntity.TASK, "t1", rig.taskFields("mismo"), at("1970-01-01T00:00:01Z"))
        dao.baseline("task", "t1", 1_000L)

        rig.store.applyPage(rig.page(listOf(rig.taskRecord("t1", "viejo", at("1970-01-01T00:00:01Z")))), USER_A, rig.now, PageCommit.SaveCursor)

        assertTrue(dao.tasks.value.getValue("t1").pending)
        assertEquals(3_000L, dao.tasks.value.getValue("t1").updatedAt)
    }

    @Test
    fun paginaDeOtraCuenta_pausaYNoAplicaNada() = runTest {
        rig.bound()
        val engine = with(rig) { engine() }
        engine.runCycle(manual = true)
        backend.userId = USER_B
        rig.seedTask("ajena", "de otra cuenta", at("2026-11-26T10:00:00Z"))

        engine.runCycle(manual = true)

        assertNull(dao.tasks.value["ajena"])
        assertTrue(engine.accountSwitchPrompt.value)
    }
}
