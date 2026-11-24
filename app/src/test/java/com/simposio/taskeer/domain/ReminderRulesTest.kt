package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.reminder.snoozed
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

    private fun sent() = newReminder("t1", now, now.minusSeconds(60), clientId = "r1")
        .copy(status = ReminderStatus.SENT, sentAt = now)

    @Test
    fun snoozed_deEnviado_creaPendienteNuevoYConservaOrigen() {
        val origin = sent()
        val result = origin.snoozed(now.plusSeconds(3_600), now, clientId = "r2")

        val next = (result as DomainResult.Success).value
        assertEquals(ReminderStatus.PENDING, next.status)
        assertEquals("t1", next.taskId)
        assertEquals(now.plusSeconds(3_600), next.remindAt)
        assertEquals("r2", next.id)
        assertNull(next.sentAt)
        assertEquals(now, next.createdAt)
        assertTrue(next.sync.pending)
        assertEquals(ReminderStatus.SENT, origin.status)
    }

    @Test
    fun snoozed_dePendienteODescartado_falla() {
        val pending = newReminder("t1", now, now)
        val dismissed = pending.copy(status = ReminderStatus.DISMISSED)

        listOf(pending, dismissed).forEach {
            val error = (it.snoozed(now.plusSeconds(60), now) as DomainResult.Failure).error
            assertEquals("REMINDER_NOT_SENT", error.code)
        }
    }

    @Test
    fun snoozed_enPasado_falla() {
        val error = (sent().snoozed(now, now) as DomainResult.Failure).error

        assertEquals("VALIDATION_ERROR", error.code)
    }
}
