package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.ReminderStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderMappingTest {

    private val at: Instant = Instant.parse("2026-11-04T10:00:00Z")

    private fun entity(status: String) = ReminderEntity(
        id = "r1",
        taskId = "t1",
        remindAt = at.plusSeconds(60).toEpochMilli(),
        status = status,
        sentAt = null,
        createdAt = at.toEpochMilli(),
        updatedAt = at.toEpochMilli(),
        pending = true,
        deletedAt = null,
    )

    @Test
    fun toReminder_yToEntity_sonSimetricos() {
        val entity = entity("SENT").copy(sentAt = at.plusSeconds(61).toEpochMilli(), pending = false)

        val reminder = entity.toReminder()

        assertEquals(at.plusSeconds(60), reminder.remindAt)
        assertEquals(ReminderStatus.SENT, reminder.status)
        assertEquals(at.plusSeconds(61), reminder.sentAt)
        assertEquals(entity, reminder.toEntity())
    }

    @Test
    fun toReminder_estadoDesconocido_sePasaADismissed() {
        assertEquals(ReminderStatus.DISMISSED, entity("SNOOZED").toReminder().status)
    }

    @Test
    fun toReminder_conservaMetadatosDeSincronizacion() {
        val reminder = entity("PENDING").toReminder()

        assertEquals("r1", reminder.id)
        assertTrue(reminder.sync.pending)
        assertNull(reminder.sync.deletedAt)
    }
}
