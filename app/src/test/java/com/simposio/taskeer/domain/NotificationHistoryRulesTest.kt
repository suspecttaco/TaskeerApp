package com.simposio.taskeer.domain

import org.junit.Assert.assertFalse
import com.simposio.taskeer.domain.model.newSyncMetadata
import com.simposio.taskeer.domain.notification.snoozedUntil
import com.simposio.taskeer.domain.model.AppNotificationKind
import com.simposio.taskeer.domain.model.NotificationEvent
import com.simposio.taskeer.domain.notification.HISTORY_LIMIT
import com.simposio.taskeer.domain.notification.badgeLabel
import com.simposio.taskeer.domain.notification.toNotification
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationHistoryRulesTest {

    private val now: Instant = Instant.parse("2026-11-04T10:00:00Z")

    @Test
    fun deadlineEvent_guardaTareaTituloYMomento() {
        val entry = NotificationEvent.Deadline("t1", "Entregar").toNotification(true, now, "n1")

        assertEquals(AppNotificationKind.DEADLINE, entry.kind)
        assertEquals("t1", entry.taskId)
        assertEquals("Entregar", entry.taskTitle)
        assertNull(entry.activeTaskCount)
        assertEquals(now, entry.createdAt)
        assertEquals("n1", entry.id)
    }

    @Test
    fun digestEvent_conservaConteoCero() {
        val entry = NotificationEvent.Digest(0).toNotification(true, now)

        assertEquals(AppNotificationKind.DIGEST, entry.kind)
        assertEquals(0, entry.activeTaskCount)
        assertNull(entry.taskId)
    }

    @Test
    fun digestEvent_conservaElConteo() {
        assertEquals(7, NotificationEvent.Digest(7).toNotification(false, now).activeTaskCount)
    }

    @Test
    fun manualEvent_guardaTareaYTitulo() {
        val entry = NotificationEvent.Manual("r1", "t2", "Llamar").toNotification(false, now)

        assertEquals(AppNotificationKind.REMINDER, entry.kind)
        assertEquals("t2", entry.taskId)
        assertEquals("Llamar", entry.taskTitle)
        assertEquals(false, entry.delivered)
    }

    @Test
    fun nuevaEntrada_esNoLeidaYPendiente() {
        val entry = NotificationEvent.Digest(1).toNotification(true, now)

        assertNull(entry.readAt)
        assertTrue(entry.isUnread)
        assertTrue(entry.sync.pending)
        assertNull(entry.sync.deletedAt)
        assertEquals(36, entry.id.length)
    }

    @Test
    fun historyLimit_es200() {
        assertEquals(200, HISTORY_LIMIT)
    }

    @Test
    fun badgeLabel_0_null() {
        assertNull(badgeLabel(0))
        assertNull(badgeLabel(-3))
    }

    @Test
    fun badgeLabel_1_99_exacto() {
        assertEquals("1", badgeLabel(1))
        assertEquals("99", badgeLabel(99))
    }

    @Test
    fun badgeLabel_100_es99Mas() {
        assertEquals("99+", badgeLabel(100))
        assertEquals("99+", badgeLabel(250))
    }

    @Test
    fun manualEvent_guardaReminderId() {
        val entry = NotificationEvent.Manual("r1", "t2", "Llamar").toNotification(true, now)

        assertEquals("r1", entry.reminderId)
        assertNull(entry.snoozedUntil)
    }

    @Test
    fun deadlineYDigest_reminderIdNulo() {
        assertNull(NotificationEvent.Deadline("t1", "A").toNotification(true, now).reminderId)
        assertNull(NotificationEvent.Digest(2).toNotification(true, now).reminderId)
    }

    @Test
    fun canSnooze_soloRecordatorioConReminderIdSinPosponer() {
        val manual = NotificationEvent.Manual("r1", "t2", "Llamar").toNotification(true, now)

        assertTrue(manual.canSnooze)
        assertFalse(manual.copy(reminderId = null).canSnooze)
        assertFalse(manual.copy(snoozedUntil = now.plusSeconds(60)).canSnooze)
        assertFalse(NotificationEvent.Deadline("t1", "A").toNotification(true, now).canSnooze)
        assertFalse(NotificationEvent.Digest(1).toNotification(true, now).canSnooze)
    }

    @Test
    fun snoozedUntil_marcaLeidaYPendiente() {
        val entry = NotificationEvent.Manual("r1", "t2", "Llamar").toNotification(true, now, "n1")
            .copy(sync = newSyncMetadata(now, "n1").copy(pending = false))
        val later = now.plusSeconds(30)
        val until = now.plusSeconds(3_600)

        val snoozed = entry.snoozedUntil(until, later)

        assertEquals(until, snoozed.snoozedUntil)
        assertEquals(later, snoozed.readAt)
        assertTrue(snoozed.sync.pending)
        assertEquals(later, snoozed.sync.updatedAt)
        assertEquals(later, snoozed.copy(readAt = later).snoozedUntil(until, later.plusSeconds(5)).readAt)
    }
}
