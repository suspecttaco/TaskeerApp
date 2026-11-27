package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.testing.SyncRig
import com.simposio.taskeer.testing.USER_A
import com.simposio.taskeer.testing.USER_B
import com.simposio.taskeer.testing.at
import com.simposio.taskeer.testing.syncList
import com.simposio.taskeer.testing.syncSubtask
import com.simposio.taskeer.testing.syncTask
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Una prueba por entidad: cómo se traduce cada registro del servidor a su fila local.
class RoomApplyPageTest {

    private val rig = SyncRig()
    private val dao get() = rig.dao
    private val updated = at("2026-11-26T10:00:00Z")

    private suspend fun apply(vararg records: ServerRecord) {
        rig.store.applyPage(rig.page(records.toList()), USER_A, rig.now, PageCommit.SaveCursor)
    }

    @Test
    fun lista_nuevaSeCreaConNombreYPosicion() = runTest {
        rig.bound()

        apply(ServerRecord(SyncEntity.LIST, "l2", updated, null, mapOf("name" to "Compras", "position" to 3, "createdAt" to "2026-11-20T10:00:00.000Z")))

        val row = dao.lists.value.getValue("l2")
        assertEquals("Compras", row.name)
        assertEquals(3, row.position)
        assertEquals(at("2026-11-20T10:00:00Z").toEpochMilli(), row.createdAt)
        assertFalse(row.pending)
        assertEquals(updated.toEpochMilli(), dao.baseline("list", "l2")!!.serverUpdatedAt)
    }

    @Test
    fun lista_tombstoneConservaLaFilaBorrada() = runTest {
        rig.bound()
        dao.put(syncList("l2", pending = false, updatedAt = 5L))
        dao.baseline("list", "l2", 5L)

        apply(ServerRecord(SyncEntity.LIST, "l2", updated, updated, mapOf("name" to "Compras")))

        assertEquals(updated.toEpochMilli(), dao.lists.value.getValue("l2").deletedAt)
    }

    @Test
    fun tareaConListId_guardaListaYSinColumna() = runTest {
        rig.bound()

        apply(rig.taskRecord("t1", "Comprar leche", updated))

        val row = dao.tasks.value.getValue("t1")
        assertEquals(rig.listId, row.listId)
        assertNull(row.columnId)
        assertEquals("Comprar leche", row.title)
        assertFalse(row.pending)
    }

    @Test
    fun tarea_asignadaAMi_seGuardaComoLocalUser() = runTest {
        rig.bound()

        apply(ServerRecord(SyncEntity.TASK, "t1", updated, null, rig.taskFields("x") + mapOf("assigneeId" to USER_A)))
        apply(ServerRecord(SyncEntity.TASK, "t2", updated, null, rig.taskFields("y") + mapOf("assigneeId" to USER_B)))

        assertEquals(LocalUser.ID, dao.tasks.value.getValue("t1").assigneeId)
        assertEquals(USER_B, dao.tasks.value.getValue("t2").assigneeId)
    }

    @Test
    fun tarea_conservaLosCincoCamposDeRepeticionYCompletada() = runTest {
        rig.bound()
        val fields = rig.taskFields("repite") + mapOf(
            "completedAt" to "2026-11-26T09:00:00.000Z", "repeatUnit" to "WEEK", "repeatInterval" to 2,
            "repeatWeekdays" to 5, "repeatAnchorDay" to 3, "repeatSuccessorId" to "sucesora",
        )

        apply(ServerRecord(SyncEntity.TASK, "t1", updated, null, fields))

        val row = dao.tasks.value.getValue("t1")
        assertEquals(listOf("WEEK", 2, 5, 3, "sucesora"), listOf(row.repeatUnit, row.repeatInterval, row.repeatWeekdays, row.repeatAnchorDay, row.repeatSuccessorId))
        assertEquals(at("2026-11-26T09:00:00Z").toEpochMilli(), row.completedAt)
    }

    @Test
    fun tarea_sinListaNiColumna_noSeCrea() = runTest {
        rig.bound()

        apply(ServerRecord(SyncEntity.TASK, "t1", updated, null, mapOf("title" to "sin forma")))

        assertNull(dao.tasks.value["t1"])
        assertNull(dao.baseline("task", "t1"))
    }

    @Test
    fun tareaDeTablero_fueraDeAlcance_noSeAplica() = runTest {
        rig.bound()

        apply(ServerRecord(SyncEntity.TASK, "t1", updated, null, rig.taskFields("de tablero") + mapOf("listId" to null, "columnId" to "c1")))

        assertNull(dao.tasks.value["t1"])
    }

    @Test
    fun subtarea_nuevaYActualizada() = runTest {
        rig.bound()
        dao.put(syncTask("t1", listId = rig.listId, pending = false))
        val fields = mapOf("taskId" to "t1", "title" to "Hija", "completed" to false, "position" to 1, "assigneeId" to null, "dueAt" to null)

        apply(ServerRecord(SyncEntity.SUBTASK, "s1", updated, null, fields))
        apply(ServerRecord(SyncEntity.SUBTASK, "s1", updated.plusSeconds(60), null, fields + mapOf("completed" to true)))

        val row = dao.subtasks.value.getValue("s1")
        assertEquals("t1", row.taskId)
        assertTrue(row.completed)
        assertFalse(row.pending)
    }

    @Test
    fun subtarea_tombstoneMarcaBorradoSinFisico() = runTest {
        rig.bound()
        dao.put(syncSubtask("s1", "t1", pending = false, updatedAt = 5L))
        dao.baseline("subtask", "s1", 5L)

        apply(ServerRecord(SyncEntity.SUBTASK, "s1", updated, updated, mapOf("taskId" to "t1")))

        assertNotNull(dao.subtasks.value.getValue("s1").deletedAt)
    }

    @Test
    fun tableroYColumna_seIgnoranHastaLaFaseDeTableros() = runTest {
        rig.bound()
        rig.scopeHolder.update(setOf(SyncEntity.LIST, SyncEntity.TASK, SyncEntity.BOARD, SyncEntity.COLUMN))
        rig.backend.supported = rig.backend.supported + setOf(SyncEntity.BOARD, SyncEntity.COLUMN)

        apply(
            ServerRecord(SyncEntity.BOARD, "b1", updated, null, mapOf("name" to "Tablero")),
            ServerRecord(SyncEntity.COLUMN, "c1", updated, null, mapOf("boardId" to "b1", "name" to "Por hacer")),
        )

        assertNull(dao.baseline("board", "b1"))
        assertNull(dao.baseline("column", "c1"))
    }
}
