package com.simposio.taskeer.data.reminder

import com.simposio.taskeer.data.repository.NotificationHistoryRepository
import com.simposio.taskeer.domain.model.AppNotificationKind
import com.simposio.taskeer.domain.model.NotificationEvent
import com.simposio.taskeer.testing.FakeNotificationHistoryRepository
import com.simposio.taskeer.testing.RecordingNotifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRecordingNotifierTest {

    private val delegate = RecordingNotifier()
    private val history = FakeNotificationHistoryRepository()
    private val notifier = HistoryRecordingNotifier(delegate, history)

    @Test
    fun notify_devuelveElValorDelDelegado() = runTest {
        delegate.shown = true
        assertTrue(notifier.notify(NotificationEvent.Digest(2)))

        delegate.shown = false
        assertFalse(notifier.notify(NotificationEvent.Digest(2)))
    }

    @Test
    fun notify_registraDeliveredSegunElDelegado() = runTest {
        delegate.shown = true
        notifier.notify(NotificationEvent.Deadline("t1", "Tarea"))
        delegate.shown = false
        notifier.notify(NotificationEvent.Manual("r1", "t1", "Tarea"))

        val entries = history.all.value
        assertEquals(listOf(true, false), entries.map { it.delivered })
        assertEquals(
            listOf(AppNotificationKind.DEADLINE, AppNotificationKind.REMINDER),
            entries.map { it.kind },
        )
    }

    @Test
    fun notify_registraUnaEntradaPorEventoYDelegaCadaUno() = runTest {
        val events = listOf(
            NotificationEvent.Deadline("t1", "A"),
            NotificationEvent.Digest(0),
            NotificationEvent.Manual("r1", "t2", "B"),
        )

        events.forEach { notifier.notify(it) }

        assertEquals(3, history.observeAll().first().size)
        assertEquals(events, delegate.events)
    }

    @Test
    fun notify_siElHistorialFalla_devuelveElResultadoDelSistema() = runTest {
        val failing = object : NotificationHistoryRepository by history {
            override suspend fun record(event: NotificationEvent, delivered: Boolean) {
                error("fallo de disco")
            }
        }
        delegate.shown = true

        assertTrue(HistoryRecordingNotifier(delegate, failing).notify(NotificationEvent.Digest(1)))
        delegate.shown = false
        assertFalse(HistoryRecordingNotifier(delegate, failing).notify(NotificationEvent.Digest(1)))
    }
}
