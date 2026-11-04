package com.simposio.taskeer.data.reminder

import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.AppNotificationKind
import com.simposio.taskeer.domain.model.DeadlineReminderSettings
import com.simposio.taskeer.domain.model.DigestSettings
import com.simposio.taskeer.domain.model.ReminderCandidate
import com.simposio.taskeer.domain.model.ReminderLead
import com.simposio.taskeer.domain.model.ReminderMechanism
import com.simposio.taskeer.domain.reminder.newReminder
import com.simposio.taskeer.testing.FakeNotificationHistoryRepository
import com.simposio.taskeer.testing.FakePreferencesRepository
import com.simposio.taskeer.testing.FakeReminderRepository
import com.simposio.taskeer.testing.FakeReminderTaskRepository
import com.simposio.taskeer.testing.InMemoryCheckpointStore
import com.simposio.taskeer.testing.RecordingNotifier
import com.simposio.taskeer.testing.RecordingReminderScheduler
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderReconcilerHistoryTest {

    private val now: Instant = Instant.parse("2026-11-04T08:10:00Z")
    private val history = FakeNotificationHistoryRepository { now }
    private val tasks = FakeReminderTaskRepository()
    private val checkpoints = InMemoryCheckpointStore()

    private fun reconciler(
        deadline: DeadlineReminderSettings = DeadlineReminderSettings(),
        digest: DigestSettings = DigestSettings(),
        reminders: FakeReminderRepository = FakeReminderRepository(),
        shown: Boolean = true,
    ) = ReminderReconciler(
        tasks = tasks,
        reminders = reminders,
        preferences = FakePreferencesRepository(deadline, digest),
        checkpoints = checkpoints,
        scheduler = RecordingReminderScheduler(),
        notifier = HistoryRecordingNotifier(RecordingNotifier(shown), history),
        timeZone = TimeZoneProvider { ZoneId.of("Europe/Madrid") },
        clock = { now },
    )

    @Test
    fun missedYDiscarded_noCreanEntrada() = runTest {
        val reminders = FakeReminderRepository(
            now = now,
            taskTitles = mapOf("t1" to "Tarea"),
            deletedTasks = setOf("gone"),
        )
        reminders.put(newReminder("t1", now.minusSeconds(7_200), now.minusSeconds(86_400), clientId = "perdido"))
        reminders.put(newReminder("gone", now.minusSeconds(60), now.minusSeconds(86_400), clientId = "descartado"))

        reconciler(reminders = reminders).reconcile()

        assertTrue(history.all.value.isEmpty())
    }

    @Test
    fun conciliacionSinEventos_noCreaEntradas() = runTest {
        reconciler().reconcile()

        assertTrue(history.all.value.isEmpty())
    }

    @Test
    fun unaEntradaPorEventoEmitido_incluidoSiElSistemaBloquea() = runTest {
        tasks.candidates.value = listOf(ReminderCandidate("t1", "Tarea t1", now.minusSeconds(60)))
        tasks.activeCount = 4
        checkpoints.write(ReminderMechanism.DEADLINE, now.minusSeconds(3_600))
        checkpoints.write(ReminderMechanism.DIGEST, now.minusSeconds(3_600))

        reconciler(
            deadline = DeadlineReminderSettings(true, ReminderLead.AT_DUE),
            digest = DigestSettings(true, setOf(DayOfWeek.WEDNESDAY), LocalTime.of(9, 0)),
            shown = false,
        ).reconcile()

        val entries = history.all.value
        assertEquals(listOf(AppNotificationKind.DEADLINE, AppNotificationKind.DIGEST), entries.map { it.kind })
        assertTrue(entries.none { it.delivered })
    }
}
