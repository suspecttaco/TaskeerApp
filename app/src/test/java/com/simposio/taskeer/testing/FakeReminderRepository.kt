package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.ReminderRepository
import com.simposio.taskeer.domain.model.ClaimResult
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.DueReminder
import com.simposio.taskeer.domain.model.Reminder
import com.simposio.taskeer.domain.model.ReminderStatus
import com.simposio.taskeer.domain.reminder.dismissed
import com.simposio.taskeer.domain.reminder.isWithinTolerance
import com.simposio.taskeer.domain.reminder.newReminder
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeReminderRepository(
    private val now: Instant = Instant.parse("2026-11-04T10:00:00Z"),
    private val taskTitles: Map<String, String> = emptyMap(),
    private val completedTasks: Set<String> = emptySet(),
    private val deletedTasks: Set<String> = emptySet(),
) : ReminderRepository {

    private val rows = MutableStateFlow<Map<String, Reminder>>(emptyMap())

    val created: MutableList<Pair<String, Instant>> = mutableListOf()

    var failure: DomainError? = null

    fun put(reminder: Reminder) {
        rows.value = rows.value + (reminder.id to reminder)
    }

    fun statusOf(id: String): ReminderStatus? = rows.value[id]?.status

    override fun observeReminder(taskId: String): Flow<Reminder?> = rows.map { all ->
        all.values.firstOrNull { it.taskId == taskId && it.status == ReminderStatus.PENDING }
    }

    override fun observePending(): Flow<List<Reminder>> = rows.map { all ->
        all.values.filter { it.status == ReminderStatus.PENDING }.sortedBy { it.remindAt }
    }

    override suspend fun createReminder(taskId: String, remindAt: Instant): DomainResult<Reminder> {
        failure?.let { return DomainResult.Failure(it) }
        created += taskId to remindAt
        val reminder = newReminder(taskId, remindAt, now)
        put(reminder)
        return DomainResult.Success(reminder)
    }

    override suspend fun cancelReminder(id: String): DomainResult<Unit> {
        val reminder = rows.value[id]
            ?: return DomainResult.Failure(DomainError.NotFound("REMINDER_NOT_FOUND", "no existe"))
        return when (val result = reminder.dismissed(now)) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> {
                put(result.value)
                DomainResult.Success(Unit)
            }
        }
    }

    override suspend fun claimDue(now: Instant): ClaimResult {
        val toNotify = mutableListOf<DueReminder>()
        val missed = mutableListOf<Reminder>()
        val discarded = mutableListOf<Reminder>()
        rows.value.values
            .filter { it.status == ReminderStatus.PENDING && !it.remindAt.isAfter(now) }
            .forEach { reminder ->
                when {
                    reminder.taskId in deletedTasks || reminder.taskId in completedTasks -> {
                        put(reminder.copy(status = ReminderStatus.DISMISSED))
                        discarded += reminder
                    }
                    !isWithinTolerance(reminder.remindAt, now) -> {
                        put(reminder.copy(status = ReminderStatus.SENT, sentAt = now))
                        missed += reminder
                    }
                    else -> {
                        put(reminder.copy(status = ReminderStatus.SENT, sentAt = now))
                        toNotify += DueReminder(reminder, taskTitles[reminder.taskId] ?: "Tarea")
                    }
                }
            }
        return ClaimResult(toNotify, missed, discarded)
    }

    override suspend fun nextPendingAt(now: Instant): Instant? =
        rows.value.values
            .filter { it.status == ReminderStatus.PENDING && it.remindAt.isAfter(now) }
            .minOfOrNull { it.remindAt }
}
