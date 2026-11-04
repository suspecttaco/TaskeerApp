package com.simposio.taskeer.data.reminder

import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.data.repository.ReminderCheckpointStore
import com.simposio.taskeer.data.repository.ReminderNotifier
import com.simposio.taskeer.data.repository.ReminderRepository
import com.simposio.taskeer.data.repository.ReminderScheduler
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.NotificationEvent
import com.simposio.taskeer.domain.model.ReminderCandidate
import com.simposio.taskeer.domain.model.ReminderMechanism
import com.simposio.taskeer.domain.reminder.dueDeadlineNotifications
import com.simposio.taskeer.domain.reminder.dueDigest
import com.simposio.taskeer.domain.reminder.nextDeadlineFireAt
import com.simposio.taskeer.domain.reminder.nextDigestAt
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ReminderReconciler(
    private val tasks: TaskRepository,
    private val reminders: ReminderRepository,
    private val preferences: PreferencesRepository,
    private val checkpoints: ReminderCheckpointStore,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
    private val timeZone: TimeZoneProvider,
    private val clock: () -> Instant,
) {
    private val mutex = Mutex()

    suspend fun reconcile(silence: Set<ReminderMechanism> = emptySet()) {
        mutex.withLock { reconcileLocked(silence) }
    }

    suspend fun onEnabled(mechanism: ReminderMechanism) {
        mutex.withLock { checkpoints.write(mechanism, clock()) }
        reconcile()
    }

    private suspend fun reconcileLocked(silence: Set<ReminderMechanism>) {
        val now = clock()
        val zone = timeZone.current()
        val deadline = preferences.deadlineReminders.first()
        val digest = preferences.digest.first()
        val events = mutableListOf<NotificationEvent>()

        var candidates: List<ReminderCandidate> = emptyList()
        if (deadline.enabled) {
            candidates = tasks.reminderCandidates()
            val checkpoint = checkpoints.read(ReminderMechanism.DEADLINE) ?: now
            if (ReminderMechanism.DEADLINE !in silence) {
                dueDeadlineNotifications(candidates, deadline.lead, checkpoint, now).forEach {
                    events += NotificationEvent.Deadline(it.taskId, it.title)
                }
            }
            checkpoints.write(ReminderMechanism.DEADLINE, maxOf(checkpoint, now))
        }

        if (digest.enabled) {
            val checkpoint = checkpoints.read(ReminderMechanism.DIGEST) ?: now
            val due = if (ReminderMechanism.DIGEST in silence) {
                emptyList()
            } else {
                dueDigest(digest, zone, checkpoint, now)
            }
            checkpoints.write(ReminderMechanism.DIGEST, maxOf(checkpoint, now))
            if (due.isNotEmpty()) {
                events += NotificationEvent.Digest(tasks.activeTaskCount())
            }
        }

        reminders.claimDue(now).toNotify.forEach {
            events += NotificationEvent.Manual(it.reminder.id, it.reminder.taskId, it.taskTitle)
        }

        events.forEach { notifier.notify(it) }

        val next = listOfNotNull(
            if (deadline.enabled) nextDeadlineFireAt(candidates, deadline.lead, now) else null,
            if (digest.enabled) nextDigestAt(digest, zone, now) else null,
            reminders.nextPendingAt(now),
        ).minOrNull()
        if (next == null) scheduler.cancel() else scheduler.scheduleAt(next)
    }
}
