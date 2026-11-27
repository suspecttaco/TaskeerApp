package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.task.successorIdFor
import com.simposio.taskeer.testing.SyncRig
import com.simposio.taskeer.testing.USER_A
import com.simposio.taskeer.testing.at
import com.simposio.taskeer.testing.syncTask
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class RepeatSyncTest {

    private val rig = SyncRig()
    private val dao get() = rig.dao
    private val backend get() = rig.backend
    private val successorId = successorIdFor("t1")
    private val due = at("2026-11-27T08:00:00Z").toEpochMilli()

    private suspend fun TestScope.cycle() = with(rig) { engine().runCycle(manual = true) }

    private fun repeating(id: String, successor: String? = null) = syncTask(id, listId = rig.listId, repeatUnit = "WEEK", repeatInterval = 2)
        .copy(repeatWeekdays = 0b0010101, repeatAnchorDay = 3, repeatSuccessorId = successor, dueAt = due)

    private fun taskCount(id: String) = dao.tasks.value.values.count { it.id == id }

    @Test
    fun cincoCamposViajan() = runTest {
        rig.bound()
        dao.put(repeating("t1", successor = successorId))

        cycle()

        val data = backend.batches.flatten().single { it.id == "t1" }.data.orEmpty()
        assertEquals("WEEK", data["repeatUnit"])
        assertEquals(2, data["repeatInterval"])
        assertEquals(0b0010101, data["repeatWeekdays"])
        assertEquals(3, data["repeatAnchorDay"])
        assertEquals(successorId, data["repeatSuccessorId"])
        assertEquals(successorId, backend.task("t1")?.fields?.get("repeatSuccessorId"))
    }

    @Test
    fun sucesoraDeterministaNoDuplica() = runTest {
        rig.bound()
        val record = ServerRecord(SyncEntity.TASK, successorId, at("2026-11-26T10:00:00Z"), null, rig.taskFields("Regar") + ("repeatUnit" to "WEEK"))

        rig.store.applyPage(rig.page(listOf(record)), USER_A, rig.now, PageCommit.SaveCursor)
        rig.store.applyPage(rig.page(listOf(record)), USER_A, rig.now, PageCommit.SaveCursor)

        assertEquals(1, taskCount(successorId))
        assertEquals("WEEK", dao.tasks.value.getValue(successorId).repeatUnit)
        assertEquals(successorId, successorIdFor("t1"))
    }

    @Test
    fun dosDispositivosCompletan_unaSucesora() = runTest {
        rig.bound()
        val other = repeating(successorId).copy(title = "Regar", pending = false)
        backend.seed(SyncEntity.TASK, successorId, rig.taskFields("Regar") + mapOf("repeatUnit" to "WEEK", "repeatInterval" to 2, "repeatWeekdays" to 0b0010101, "repeatAnchorDay" to 3, "dueAt" to "2026-11-27T08:00:00.000Z"), at("2026-11-26T09:00:00Z"))
        dao.put(repeating("t1", successor = successorId).copy(completedAt = due, title = "Regar"))
        dao.put(other.copy(pending = true, repeatSuccessorId = null))

        cycle()

        assertEquals(1, backend.records.keys.count { it == SyncEntity.TASK to successorId })
        assertEquals(1, taskCount(successorId))
        assertFalse(dao.tasks.value.getValue(successorId).pending)
        assertNotNull(dao.baseline("task", successorId))
        assertEquals(successorId, backend.task("t1")?.fields?.get("repeatSuccessorId"))
    }
}
