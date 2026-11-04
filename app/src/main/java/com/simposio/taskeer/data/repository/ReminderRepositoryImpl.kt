package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.ReminderDao
import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.toEntity
import com.simposio.taskeer.data.local.toReminder
import com.simposio.taskeer.data.local.toTask
import com.simposio.taskeer.domain.model.ClaimResult
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.DueReminder
import com.simposio.taskeer.domain.model.Reminder
import com.simposio.taskeer.domain.model.ReminderStatus
import com.simposio.taskeer.domain.reminder.REMINDER_ALREADY_PENDING
import com.simposio.taskeer.domain.reminder.REMINDER_NOT_FOUND
import com.simposio.taskeer.domain.reminder.dismissed
import com.simposio.taskeer.domain.reminder.isWithinTolerance
import com.simposio.taskeer.domain.reminder.newReminder
import com.simposio.taskeer.domain.reminder.validateRemindAt
import com.simposio.taskeer.domain.task.TASK_NOT_FOUND
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReminderRepositoryImpl(
    private val reminderDao: ReminderDao,
    private val taskDao: TaskDao,
    private val now: () -> Instant = Instant::now,
) : ReminderRepository {

    override fun observeReminder(taskId: String): Flow<Reminder?> =
        reminderDao.observePendingByTask(taskId).map { it?.toReminder() }

    override suspend fun createReminder(taskId: String, remindAt: Instant): DomainResult<Reminder> {
        val current = now()
        validateRemindAt(remindAt, current)?.let { return DomainResult.Failure(it) }
        val task = taskDao.findById(taskId)?.toTask()?.takeUnless { it.isDeleted }
        if (task == null) {
            return DomainResult.Failure(DomainError.NotFound(TASK_NOT_FOUND, "La tarea no existe"))
        }
        if (reminderDao.findPendingByTask(taskId) != null) {
            return DomainResult.Failure(
                DomainError.Conflict(
                    REMINDER_ALREADY_PENDING,
                    "La tarea ya tiene un recordatorio pendiente",
                ),
            )
        }
        val reminder = newReminder(taskId, remindAt, current)
        reminderDao.upsert(reminder.toEntity())
        return DomainResult.Success(reminder)
    }

    override suspend fun cancelReminder(id: String): DomainResult<Unit> {
        val reminder = reminderDao.findById(id)?.toReminder()
            ?: return DomainResult.Failure(
                DomainError.NotFound(REMINDER_NOT_FOUND, "El recordatorio no existe"),
            )
        return when (val result = reminder.dismissed(now())) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> {
                reminderDao.upsert(result.value.toEntity())
                DomainResult.Success(Unit)
            }
        }
    }

    override fun observePending(): Flow<List<Reminder>> =
        reminderDao.observeAllPending().map { rows -> rows.map { it.toReminder() } }

    override suspend fun claimDue(now: Instant): ClaimResult {
        val nowMillis = now.toEpochMilli()
        val toNotify = mutableListOf<DueReminder>()
        val missed = mutableListOf<Reminder>()
        val discarded = mutableListOf<Reminder>()
        reminderDao.findPendingUpTo(nowMillis).map { it.toReminder() }.forEach { reminder ->
            val state = taskDao.findReminderState(reminder.taskId)
            when {
                state == null || state.deleted || state.completed -> {
                    if (reminderDao.markDismissed(reminder.id, nowMillis) == 1) {
                        discarded += reminder.copy(status = ReminderStatus.DISMISSED)
                    }
                }
                !isWithinTolerance(reminder.remindAt, now) -> {
                    if (reminderDao.markSent(reminder.id, nowMillis) == 1) {
                        missed += reminder.copy(status = ReminderStatus.SENT, sentAt = now)
                    }
                }
                reminderDao.markSent(reminder.id, nowMillis) == 1 -> {
                    val sent = reminder.copy(status = ReminderStatus.SENT, sentAt = now)
                    toNotify += DueReminder(sent, state.title)
                }
            }
        }
        return ClaimResult(toNotify, missed, discarded)
    }

    override suspend fun nextPendingAt(now: Instant): Instant? =
        reminderDao.minPendingAfter(now.toEpochMilli())?.let(Instant::ofEpochMilli)
}
