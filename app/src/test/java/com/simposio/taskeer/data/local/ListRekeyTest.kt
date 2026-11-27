package com.simposio.taskeer.data.local

import com.simposio.taskeer.data.sync.SyncMetaKeys
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.testing.FakeSyncDao
import com.simposio.taskeer.testing.syncList
import com.simposio.taskeer.testing.syncTask
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListRekeyTest {

    private val dao = FakeSyncDao()

    @Test
    fun adoptMueveTareasYNoEnviaDelete() = runTest {
        dao.put(syncList("local", pending = true))
        dao.put(syncTask("t1", listId = "local", pending = false))
        dao.put(syncTask("t2", listId = "local", pending = false))
        dao.baseline("list", "local", 5L)
        val server = TaskListEntity("servidor", "Tareas", 0, createdAt = 0L, updatedAt = 9_000L, pending = false, deletedAt = null)

        dao.adoptList("local", server, 9_000L, mutationId = null)

        assertNull(dao.lists.value["local"])
        assertEquals(setOf("servidor"), dao.tasks.value.values.map { it.listId }.toSet())
        assertTrue(dao.tasks.value.values.all { it.pending })
        assertNull(dao.baseline("list", "local"))
        assertEquals(9_000L, dao.baseline("list", "servidor")!!.serverUpdatedAt)
        assertTrue(dao.pendingLists().none { it.deletedAt != null })
        assertNull(dao.meta(SyncMetaKeys.BOUND_USER_ID))
        assertTrue(dao.queue().none { it.entity == SyncEntity.LIST.wireName && it.op == "DELETE" })
    }
}
