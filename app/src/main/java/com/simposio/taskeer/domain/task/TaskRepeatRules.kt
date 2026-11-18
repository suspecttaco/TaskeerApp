package com.simposio.taskeer.domain.task

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.RepeatUnit
import com.simposio.taskeer.domain.model.Reminder
import com.simposio.taskeer.domain.model.Subtask
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskRepeat
import com.simposio.taskeer.domain.model.newClientId
import com.simposio.taskeer.domain.model.newSyncMetadata
import com.simposio.taskeer.domain.reminder.newReminder
import com.simposio.taskeer.domain.reminder.validateRemindAt
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID

// Códigos locales: la API no tiene recurrencia (ver alineación con el backend).
const val TASK_REPEAT_INVALID = "TASK_REPEAT_INVALID"
const val TASK_REPEAT_REQUIRES_DUE = "TASK_REPEAT_REQUIRES_DUE"

const val REPEAT_MAX_INTERVAL = 999

enum class RepeatPreset { NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY, CUSTOM }

object RepeatPresets {
    val workdays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    )

    fun daily(anchorDay: Int) = TaskRepeat(RepeatUnit.DAY, 1, emptySet(), anchorDay)

    fun weekdays(anchorDay: Int) = TaskRepeat(RepeatUnit.WEEK, 1, workdays, anchorDay)

    fun weekly(anchorDay: Int) = TaskRepeat(RepeatUnit.WEEK, 1, emptySet(), anchorDay)

    fun monthly(anchorDay: Int) = TaskRepeat(RepeatUnit.MONTH, 1, emptySet(), anchorDay)

    fun yearly(anchorDay: Int) = TaskRepeat(RepeatUnit.YEAR, 1, emptySet(), anchorDay)

    fun of(preset: RepeatPreset, anchorDay: Int): TaskRepeat? = when (preset) {
        RepeatPreset.NONE, RepeatPreset.CUSTOM -> null
        RepeatPreset.DAILY -> daily(anchorDay)
        RepeatPreset.WEEKDAYS -> weekdays(anchorDay)
        RepeatPreset.WEEKLY -> weekly(anchorDay)
        RepeatPreset.MONTHLY -> monthly(anchorDay)
        RepeatPreset.YEARLY -> yearly(anchorDay)
    }
}

fun presetOf(repeat: TaskRepeat?): RepeatPreset = when {
    repeat == null -> RepeatPreset.NONE
    repeat.interval != 1 -> RepeatPreset.CUSTOM
    repeat.unit == RepeatUnit.DAY -> RepeatPreset.DAILY
    repeat.unit == RepeatUnit.WEEK && repeat.weekdays.isEmpty() -> RepeatPreset.WEEKLY
    repeat.unit == RepeatUnit.WEEK && repeat.weekdays == RepeatPresets.workdays -> RepeatPreset.WEEKDAYS
    repeat.unit == RepeatUnit.MONTH -> RepeatPreset.MONTHLY
    repeat.unit == RepeatUnit.YEAR -> RepeatPreset.YEARLY
    else -> RepeatPreset.CUSTOM
}

fun validateRepeat(repeat: TaskRepeat): DomainError? = when {
    repeat.interval !in 1..REPEAT_MAX_INTERVAL -> invalid("El intervalo debe estar entre 1 y $REPEAT_MAX_INTERVAL")
    repeat.weekdays.isNotEmpty() && repeat.unit != RepeatUnit.WEEK ->
        invalid("Los días de la semana solo se admiten al repetir por semanas")
    repeat.anchorDay !in 1..31 -> invalid("El día ancla debe estar entre 1 y 31")
    else -> null
}

fun repeatFailure(repeat: TaskRepeat?, dueAt: Instant?): DomainError? {
    if (repeat == null) return null
    validateRepeat(repeat)?.let { return it }
    return if (dueAt == null) {
        DomainError.Validation(TASK_REPEAT_REQUIRES_DUE, "Para repetir una tarea hay que elegir una fecha límite")
    } else {
        null
    }
}

fun nextDueAt(repeat: TaskRepeat, dueAt: Instant, now: Instant, zone: ZoneId): Instant {
    val local = dueAt.atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    var candidate = repeat.after(local.toLocalDate())
    while (candidate < today) candidate = repeat.after(candidate)
    // atZone resuelve el DST: hora inexistente -> la válida posterior; hora repetida -> la primera.
    return LocalDateTime.of(candidate, local.toLocalTime()).atZone(zone).toInstant()
}

fun successorIdFor(taskId: String): String =
    UUID.nameUUIDFromBytes("taskeer:repeat:$taskId".toByteArray(Charsets.UTF_8)).toString()

fun Task.successor(now: Instant, zone: ZoneId, columnId: String?, listId: String?, position: Int): Task? {
    val rule = repeat ?: return null
    val due = dueAt ?: return null
    return Task(
        columnId = columnId,
        listId = listId,
        title = title,
        description = description,
        priority = priority,
        dueAt = nextDueAt(rule, due, now, zone),
        assigneeId = assigneeId,
        position = position,
        createdAt = now,
        completedAt = null,
        repeat = rule,
        repeatSuccessorId = null,
        sync = newSyncMetadata(now, successorIdFor(id)),
    )
}

fun Task.successorSubtasks(
    successor: Task,
    subtasks: List<Subtask>,
    zone: ZoneId,
    now: Instant,
): List<Subtask> {
    val from = requireNotNull(dueAt).atZone(zone).toLocalDate()
    val to = requireNotNull(successor.dueAt).atZone(zone).toLocalDate()
    val shiftDays = ChronoUnit.DAYS.between(from, to)
    return subtasks.filter { !it.isDeleted }.map { subtask ->
        Subtask(
            taskId = successor.id,
            title = subtask.title,
            completed = false,
            position = subtask.position,
            assigneeId = subtask.assigneeId,
            dueAt = subtask.dueAt?.let { shifted(it, shiftDays, zone) },
            createdAt = now,
            sync = newSyncMetadata(now, newClientId()),
        )
    }
}

fun Task.successorReminder(successor: Task, original: Reminder?, now: Instant): Reminder? {
    val due = dueAt ?: return null
    val successorDue = successor.dueAt ?: return null
    val source = original ?: return null
    val offset = Duration.between(due, source.remindAt)
    val remindAt = successorDue.plus(offset)
    if (validateRemindAt(remindAt, now) != null) return null
    return newReminder(successor.id, remindAt, now)
}

private fun shifted(instant: Instant, days: Long, zone: ZoneId): Instant {
    val local = instant.atZone(zone)
    return LocalDateTime.of(local.toLocalDate().plusDays(days), local.toLocalTime()).atZone(zone).toInstant()
}

private fun TaskRepeat.after(date: LocalDate): LocalDate = when (unit) {
    RepeatUnit.DAY -> date.plusDays(interval.toLong())
    RepeatUnit.WEEK -> afterWeekly(date)
    RepeatUnit.MONTH -> onAnchor(YearMonth.from(date).plusMonths(interval.toLong()))
    RepeatUnit.YEAR -> onAnchor(YearMonth.from(date).plusYears(interval.toLong()))
}

private fun TaskRepeat.afterWeekly(date: LocalDate): LocalDate {
    if (weekdays.isEmpty()) return date.plusWeeks(interval.toLong())
    val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val sameWeek = weekdays.map { monday.plusDays((it.value - 1).toLong()) }.filter { it > date }.minOrNull()
    if (sameWeek != null) return sameWeek
    val firstDay = weekdays.minOf { it.value } - 1
    return monday.plusWeeks(interval.toLong()).plusDays(firstDay.toLong())
}

private fun TaskRepeat.onAnchor(month: YearMonth): LocalDate =
    month.atDay(minOf(anchorDay, month.lengthOfMonth()))

private fun invalid(message: String): DomainError = DomainError.Validation(TASK_REPEAT_INVALID, message)
