package com.simposio.taskeer.data.sync

import com.simposio.taskeer.data.repository.BoardPermissionGate
import com.simposio.taskeer.data.repository.BoardRoles
import com.simposio.taskeer.data.repository.RoleSignalSync
import com.simposio.taskeer.data.repository.SyncRepository
import com.simposio.taskeer.data.repository.UnavailableSyncRepository
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.workspace.BoardRoleRules
import com.simposio.taskeer.testing.FakeRoleChangeSignals
import com.simposio.taskeer.testing.SyncRig
import com.simposio.taskeer.testing.USER_A
import com.simposio.taskeer.testing.at
import com.simposio.taskeer.testing.syncBoard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoleSignalSyncTest {

    @Test
    fun roleChanged_disparaPull() = runTest {
        val signals = FakeRoleChangeSignals()
        val sync = CountingSync()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { RoleSignalSync(signals, sync).run() }

        signals.emit()
        signals.emit()

        assertEquals(2, sync.requests)
    }

    @Test
    fun rolNuevoSeAplicaAlKanban() = runTest {
        val rig = SyncRig()
        rig.bound()
        rig.dao.put(syncBoard("b1", workspaceId = "ws", pending = false, updatedAt = 5L))
        val scope = setOf(SyncEntity.LIST, SyncEntity.TASK, SyncEntity.SUBTASK, SyncEntity.BOARD, SyncEntity.COLUMN)
        val access = ServerRecordFactory.access("b1", "ws", "EDITOR", at("2026-11-26T10:00:00Z"))
        rig.store.applyPage(rig.page(listOf(access), supported = scope), USER_A, rig.now, PageCommit.SaveCursor)
        val gate = BoardPermissionGate(
            object : BoardRoles {
                override suspend fun roleOf(boardId: String) = rig.dao.access.value[boardId]?.let { BoardRoleRules.parseRole(it.effectiveRole) }

                override fun observeRole(boardId: String) = flowOf(null)
            },
        )
        assertNull(gate.denyTaskChange("b1"))

        val downgraded = ServerRecordFactory.access("b1", "ws", "VIEWER", at("2026-11-26T11:00:00Z"))
        rig.store.applyPage(rig.page(listOf(downgraded), supported = scope), USER_A, rig.now, PageCommit.SaveCursor)

        assertEquals("VIEWER", rig.dao.access.value.getValue("b1").effectiveRole)
        assertNotNull(gate.denyTaskChange("b1"))
    }
}

private class CountingSync : SyncRepository by UnavailableSyncRepository {
    var requests = 0

    override fun requestSync() {
        requests++
    }
}

private object ServerRecordFactory {
    fun access(boardId: String, workspaceId: String, role: String, at: java.time.Instant) =
        ServerRecord(SyncEntity.BOARD_ACCESS, boardId, at, null, mapOf("workspaceId" to workspaceId, "effectiveRole" to role))
}
