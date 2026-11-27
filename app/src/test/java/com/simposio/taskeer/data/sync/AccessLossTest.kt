package com.simposio.taskeer.data.sync

import com.simposio.taskeer.data.local.BoardAccessEntity
import com.simposio.taskeer.data.local.SyncWorkspaceEntity
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncLoss
import com.simposio.taskeer.testing.SyncRig
import com.simposio.taskeer.testing.USER_A
import com.simposio.taskeer.testing.at
import com.simposio.taskeer.testing.syncBoard
import com.simposio.taskeer.testing.syncColumn
import com.simposio.taskeer.testing.syncSubtask
import com.simposio.taskeer.testing.syncTask
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessLossTest {

    private val rig = SyncRig()
    private val dao get() = rig.dao
    private val ws = "a0000000-0000-4000-8000-000000000002"
    private val lostAt = at("2026-11-26T18:10:00Z")
    private val scope = setOf(SyncEntity.LIST, SyncEntity.TASK, SyncEntity.SUBTASK, SyncEntity.BOARD, SyncEntity.COLUMN)

    private fun board(id: String, pending: Boolean = false) {
        dao.put(syncBoard(id, workspaceId = ws, pending = pending, updatedAt = 5L))
        dao.put(syncColumn("c-$id", id, pending = pending, updatedAt = 5L))
        dao.put(syncTask("t-$id", listId = null, columnId = "c-$id", pending = pending, updatedAt = 5L))
        dao.put(syncSubtask("s-$id", "t-$id", pending = pending, updatedAt = 5L))
        dao.put(BoardAccessEntity(id, ws, "EDITOR", 5L))
        listOf("board" to id, "column" to "c-$id", "task" to "t-$id", "subtask" to "s-$id").forEach { dao.baseline(it.first, it.second, 5L) }
    }

    private fun accessTombstone(boardId: String) =
        ServerRecord(SyncEntity.BOARD_ACCESS, boardId, lostAt, lostAt, mapOf("workspaceId" to ws, "effectiveRole" to "VIEWER"))

    private suspend fun apply(vararg records: ServerRecord) =
        rig.store.applyPage(rig.page(records.toList(), supported = scope), USER_A, rig.now, PageCommit.SaveCursor)

    @Test
    fun tombstoneDeAcceso_retiraTableroYDescendientes() = runTest {
        rig.bound()
        board("b1")

        val effect = apply(accessTombstone("b1"))

        assertNull(dao.boards.value["b1"])
        assertTrue(dao.columns.value.isEmpty())
        assertTrue(dao.tasks.value.isEmpty())
        assertTrue(dao.subtasks.value.isEmpty())
        assertNull(dao.access.value["b1"])
        assertEquals(listOf(SyncLoss.ACCESS_LOST), effect.losses)
    }

    @Test
    fun descartaOperacionesPendientes() = runTest {
        rig.bound()
        board("b1", pending = true)
        rig.store.collect(rig.now)
        assertEquals(4, dao.queueRows.value.size)

        apply(accessTombstone("b1"))

        assertTrue(dao.queueRows.value.isEmpty())
        assertNull(dao.baseline("task", "t-b1"))
        assertNull(dao.baseline("board", "b1"))
    }

    @Test
    fun noVuelveASubir() = runTest {
        rig.bound()
        board("b1", pending = true)
        rig.backend.supported = scope
        dao.metas.value = dao.metas.value + (SyncMetaKeys.MARKED_SCOPE to "list,task,subtask,board,column")
        apply(accessTombstone("b1"))

        with(rig) { engine().runCycle(manual = true) }

        assertTrue(rig.backend.batches.flatten().none { it.id in setOf("b1", "c-b1", "t-b1", "s-b1") })
        assertNull(dao.boards.value["b1"])
    }

    @Test
    fun workspaceBorrado_retiraSusTableros() = runTest {
        rig.bound()
        dao.put(SyncWorkspaceEntity(ws, "Equipo", isPersonal = false, myRole = "EDITOR", deletedAt = null))
        board("b1")
        board("b2")
        val tombstone = ServerRecord(SyncEntity.WORKSPACE, ws, lostAt, lostAt, mapOf("name" to "Equipo", "isPersonal" to false, "myRole" to "EDITOR"))

        val effect = apply(tombstone)

        assertTrue(dao.boards.value.isEmpty())
        assertTrue(dao.tasks.value.isEmpty())
        assertEquals(lostAt.toEpochMilli(), dao.workspaces.value.getValue(ws).deletedAt)
        assertEquals(2, effect.losses.count { it == SyncLoss.ACCESS_LOST })
    }

    @Test
    fun restoDeTablerosIntacto() = runTest {
        rig.bound()
        board("b1")
        board("b2")

        apply(accessTombstone("b1"))

        assertNotNull(dao.boards.value["b2"])
        assertNotNull(dao.columns.value["c-b2"])
        assertNotNull(dao.tasks.value["t-b2"])
        assertNotNull(dao.subtasks.value["s-b2"])
        assertNotNull(dao.access.value["b2"])
        assertEquals(5L, dao.baseline("task", "t-b2")?.serverUpdatedAt)
    }
}
