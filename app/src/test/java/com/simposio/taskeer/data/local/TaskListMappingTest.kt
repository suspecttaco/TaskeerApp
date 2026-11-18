package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.TaskList
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskListMappingTest {

    private val instant = Instant.ofEpochMilli(1_721_000_000_123L)

    @Test
    fun ida_yVuelta() {
        val list = TaskList(
            name = "Compras",
            position = 3,
            createdAt = instant,
            sync = SyncMetadata("lista-1", instant.plusSeconds(10), pending = true, deletedAt = null),
        )

        val entity = list.toEntity()

        assertEquals("lista-1", entity.id)
        assertEquals("Compras", entity.name)
        assertEquals(3, entity.position)
        assertEquals(instant.toEpochMilli(), entity.createdAt)
        assertEquals(instant.plusSeconds(10).toEpochMilli(), entity.updatedAt)
        assertTrue(entity.pending)
        assertNull(entity.deletedAt)
        assertEquals(list, entity.toTaskList())
        assertFalse(entity.toTaskList().isDeleted)
    }

    @Test
    fun borrada_conservaDeletedAt() {
        val deletedAt = instant.plusSeconds(30)
        val list = TaskList("Compras", 1, instant, SyncMetadata("lista-1", deletedAt, pending = true, deletedAt = deletedAt))

        val roundTrip = list.toEntity().toTaskList()

        assertEquals(deletedAt.toEpochMilli(), list.toEntity().deletedAt)
        assertEquals(deletedAt, roundTrip.sync.deletedAt)
        assertTrue(roundTrip.isDeleted)
    }
}
