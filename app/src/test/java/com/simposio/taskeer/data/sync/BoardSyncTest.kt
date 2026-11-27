package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncOp
import com.simposio.taskeer.testing.SyncRig
import com.simposio.taskeer.testing.USER_A
import com.simposio.taskeer.testing.at
import com.simposio.taskeer.testing.syncBoard
import com.simposio.taskeer.testing.syncColumn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardSyncTest {

    private val rig = SyncRig()
    private val dao get() = rig.dao
    private val backend get() = rig.backend
    private val personal = "a0000000-0000-4000-8000-000000000001"
    private val shared = "a0000000-0000-4000-8000-000000000002"

    private fun withBoards() {
        rig.bound()
        backend.supported = backend.supported + setOf(SyncEntity.BOARD, SyncEntity.COLUMN)
        dao.metas.value = dao.metas.value + (SyncMetaKeys.MARKED_SCOPE to "list,task,subtask,board,column")
        backend.seed(SyncEntity.WORKSPACE, personal, mapOf("name" to "Personal", "isPersonal" to true, "myRole" to "OWNER"), at("2026-11-26T09:00:00Z"))
    }

    private suspend fun TestScope.cycle() = with(rig) { engine().runCycle(manual = true) }

    private fun boardMutations() = backend.batches.flatten().filter { it.entity == SyncEntity.BOARD }

    @Test
    fun tableroLocalNuevo_vaAlPersonalConColumnas() = runTest {
        withBoards()
        dao.put(syncBoard("b1", "Sprint 12"))
        dao.put(syncColumn("c1", "b1", "Por hacer", position = 0))
        dao.put(syncColumn("c2", "b1", "Hecho", position = 1, isDone = true))

        cycle()

        val sent = boardMutations().single()
        assertEquals(SyncOp.UPSERT, sent.op)
        assertEquals(personal, sent.data?.get("workspaceId"))
        assertEquals(listOf("c1", "c2"), (sent.data?.get("columns") as List<*>).map { (it as Map<*, *>)["id"] })
        assertEquals(personal, dao.boards.value.getValue("b1").workspaceId)
        assertFalse(dao.boards.value.getValue("b1").pending)
        assertNotNull(dao.baseline("column", "c1"))
        assertFalse(dao.columns.value.getValue("c2").pending)
    }

    @Test
    fun tableroAjeno_seMaterializaConRol() = runTest {
        withBoards()
        backend.seed(SyncEntity.WORKSPACE, shared, mapOf("name" to "Equipo", "isPersonal" to false, "myRole" to "VIEWER"), at("2026-11-26T09:10:00Z"))
        backend.seed(SyncEntity.BOARD_ACCESS, "bx", mapOf("workspaceId" to shared, "effectiveRole" to "EDITOR"), at("2026-11-26T09:20:00Z"))
        backend.seed(SyncEntity.BOARD, "bx", mapOf("workspaceId" to shared, "name" to "Compartido"), at("2026-11-26T09:20:00Z"))
        backend.seed(SyncEntity.COLUMN, "cx", mapOf("boardId" to "bx", "name" to "Backlog", "position" to 0, "wipLimit" to null, "isDoneColumn" to false), at("2026-11-26T09:20:00Z"))
        rig.seedTask("tx", "tarea ajena", at("2026-11-26T09:30:00Z")).also {
            backend.seed(SyncEntity.TASK, "tx", rig.taskFields("tarea ajena") + mapOf("listId" to null, "columnId" to "cx"), at("2026-11-26T09:30:00Z"))
        }

        cycle()

        val board = dao.boards.value.getValue("bx")
        assertEquals("Compartido", board.name)
        assertEquals(shared, board.workspaceId)
        assertFalse(board.pending)
        assertEquals("EDITOR", dao.access.value.getValue("bx").effectiveRole)
        assertEquals("bx", dao.columns.value.getValue("cx").boardId)
        assertEquals("cx", dao.tasks.value.getValue("tx").columnId)
        assertFalse(dao.workspaces.value.getValue(shared).isPersonal)
        assertTrue(boardMutations().isEmpty())
    }

    @Test
    fun columnasConIdsDeCliente() = runTest {
        withBoards()
        dao.put(syncBoard("b1"))
        dao.put(syncColumn("col-cliente-1", "b1"))

        cycle()

        assertEquals(setOf("col-cliente-1"), backend.records.keys.filter { it.first == SyncEntity.COLUMN }.map { it.second }.toSet())
        assertNull(backend.records[SyncEntity.COLUMN to "otra"])
    }

    @Test
    fun renombrarTablero() = runTest {
        withBoards()
        backend.seed(SyncEntity.BOARD, "b1", mapOf("workspaceId" to personal, "name" to "Viejo"), at("2026-11-26T09:00:00Z"))
        dao.put(syncBoard("b1", "Nuevo", workspaceId = personal, updatedAt = at("2026-11-26T18:00:00Z").toEpochMilli()))
        dao.baseline("board", "b1", at("2026-11-26T09:00:00Z").toEpochMilli())

        cycle()

        assertEquals(mapOf<String, Any?>("name" to "Nuevo"), boardMutations().single().data)
        assertEquals("Nuevo", backend.records.getValue(SyncEntity.BOARD to "b1").fields["name"])
        assertFalse(dao.boards.value.getValue("b1").pending)
    }

    @Test
    fun borrarColumnaVacia() = runTest {
        withBoards()
        backend.seed(SyncEntity.BOARD, "b1", mapOf("workspaceId" to personal, "name" to "Sprint"), at("2026-11-26T09:00:00Z"))
        val columnFields = mapOf("boardId" to "b1", "name" to "Por hacer", "position" to 0, "wipLimit" to null, "isDoneColumn" to false)
        backend.seed(SyncEntity.COLUMN, "c1", columnFields, at("2026-11-26T09:00:00Z"))
        dao.put(syncBoard("b1", workspaceId = personal, pending = false, updatedAt = 5L))
        dao.baseline("board", "b1", at("2026-11-26T09:00:00Z").toEpochMilli())
        dao.put(syncColumn("c1", "b1", updatedAt = at("2026-11-26T18:00:00Z").toEpochMilli(), deletedAt = at("2026-11-26T18:00:00Z").toEpochMilli()))
        dao.baseline("column", "c1", at("2026-11-26T09:00:00Z").toEpochMilli())

        cycle()

        val sent = backend.batches.flatten().single { it.entity == SyncEntity.COLUMN }
        assertEquals(SyncOp.DELETE, sent.op)
        assertNotNull(backend.records.getValue(SyncEntity.COLUMN to "c1").deletedAt)
        assertFalse(dao.columns.value.getValue("c1").pending)
        assertEquals(USER_A, backend.userId)
    }

    @Test
    fun v17_cargaUtilDelAlta_coincideConElVector() = runTest {
        withBoards()
        backend.seed(SyncEntity.WORKSPACE, "a0000000-0000-4000-8000-000000000001", mapOf("name" to "Personal", "isPersonal" to true, "myRole" to "OWNER"), at("2026-11-26T09:00:00Z"))
        dao.put(syncBoard("20000000-0000-4000-8000-000000000001", "Sprint 12"))
        dao.put(syncColumn("40000000-0000-4000-8000-000000000001", "20000000-0000-4000-8000-000000000001", "Por hacer", position = 0))
        dao.put(syncColumn("40000000-0000-4000-8000-000000000002", "20000000-0000-4000-8000-000000000001", "Hecho", position = 1, isDone = true))
        dao.seedPersonalForPayload("a0000000-0000-4000-8000-000000000001")
        rig.store.collect(rig.now)
        val op = rig.store.eligible(rig.now).single { it.entity == SyncEntity.BOARD }

        val data = rig.store.payloadFor(op, USER_A)!!.mutation.data

        assertEquals("a0000000-0000-4000-8000-000000000001", data?.get("workspaceId"))
        assertEquals("Sprint 12", data?.get("name"))
        val columns = data?.get("columns") as List<*>
        assertEquals(
            mapOf("id" to "40000000-0000-4000-8000-000000000002", "name" to "Hecho", "position" to 1, "wipLimit" to null, "isDoneColumn" to true),
            columns[1],
        )
    }
}

private fun com.simposio.taskeer.testing.FakeSyncDao.seedPersonalForPayload(id: String) =
    put(com.simposio.taskeer.data.local.SyncWorkspaceEntity(id, "Personal", isPersonal = true, myRole = "OWNER", deletedAt = null))
