package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.ReminderStatus
import com.simposio.taskeer.domain.reminder.dismissed
import com.simposio.taskeer.domain.reminder.newReminder
import com.simposio.taskeer.domain.reminder.validateRemindAt
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderRulesTest {

    private val now: Instant = Instant.parse("2026-11-04T10:00:00Z")

    @Test
    fun validateRemindAt_anteriorAAhora_devuelveValidationError() {
        val error = validateRemindAt(now.minusSeconds(1), now)

        assertTrue(error is DomainError.Validation)
        assertEquals("VALIDATION_ERROR", error?.code)
    }

    @Test
    fun validateRemindAt_igualAAhora_devuelveValidationError() {
        assertEquals("VALIDATION_ERROR", validateRemindAt(now, now)?.code)
    }

    @Test
    fun validateRemindAt_posteriorAAhora_noDevuelveError() {
        assertNull(validateRemindAt(now.plusMillis(1), now))
    }

    @Test
    fun newReminder_nacePendienteSinEnvioYConMarcaPendiente() {
        val reminder = newReminder("t1", now.plusSeconds(60), now, clientId = "r1")

        assertEquals(ReminderStatus.PENDING, reminder.status)
        assertNull(reminder.sentAt)
        assertEquals("r1", reminder.id)
        assertTrue(reminder.sync.pending)
    }

    @Test
    fun dismissed_deUnPendiente_loPasaADismissed() {
        val reminder = newReminder("t1", now.plusSeconds(60), now)

        val result = reminder.dismissed(now.plusSeconds(5)) as DomainResult.Success

        assertEquals(ReminderStatus.DISMISSED, result.value.status)
        assertEquals(now.plusSeconds(5), result.value.sync.updatedAt)
    }

    @Test
    fun dismissed_deUnEnviado_devuelveReminderNotPending() {
        val sent = newReminder("t1", now.plusSeconds(60), now).copy(status = ReminderStatus.SENT)

        val error = (sent.dismissed(now) as DomainResult.Failure).error

        assertTrue(error is DomainError.Validation)
        assertEquals("REMINDER_NOT_PENDING", error.code)
    }
}
