package com.simposio.taskeer.domain.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class TaskDue(val taskId: String, val title: String, val dueAt: Instant, val place: String?)

sealed interface DayItem {
    val sortTitle: String

    data class Event(
        val event: DeviceEvent,
        val calendar: DeviceCalendar?,
        val continuesFromPrevious: Boolean,
        val continuesToNext: Boolean,
    ) : DayItem {
        override val sortTitle: String get() = event.title
    }

    data class Task(val task: TaskDue, val time: LocalTime) : DayItem {
        override val sortTitle: String get() = task.title
    }
}

data class CalendarDay(val date: LocalDate, val items: List<DayItem>)

data class DayMarks(val events: Int, val tasks: Int)

fun dayMarks(day: CalendarDay): DayMarks =
    DayMarks(events = day.items.count { it is DayItem.Event }, tasks = day.items.count { it is DayItem.Task })

fun buildDays(
    span: DateSpan,
    events: List<DeviceEvent>,
    calendars: List<DeviceCalendar>,
    hiddenIds: Set<Long>,
    tasks: List<TaskDue>,
    zone: ZoneId,
    includeEmpty: Boolean,
): List<CalendarDay> {
    val calendarById = calendars.associateBy { it.id }
    val visible = events.filter { it.calendarId !in hiddenIds }
    val tasksByDay = tasks.groupBy { it.dueAt.atZone(zone).toLocalDate() }
    return span.days
        .map { date ->
            val items = visible.mapNotNull { eventItemOn(it, calendarById[it.calendarId], date, zone) } +
                tasksByDay[date].orEmpty().map { DayItem.Task(it, it.dueAt.atZone(zone).toLocalTime()) }
            CalendarDay(date, items.sortedWith(dayItemOrder(zone)))
        }
        .filter { includeEmpty || it.items.isNotEmpty() }
}

// Los límites del día salen de atStartOfDay: un día de 23 o 25 horas no desplaza el solape.
private fun eventItemOn(event: DeviceEvent, calendar: DeviceCalendar?, date: LocalDate, zone: ZoneId): DayItem.Event? =
    when (val span = event.span) {
        is EventSpan.AllDay ->
            if (date >= span.start && date < maxOf(span.endExclusive, span.start.plusDays(1))) {
                DayItem.Event(event, calendar, continuesFromPrevious = false, continuesToNext = false)
            } else {
                null
            }
        is EventSpan.Timed -> {
            val windowStart = date.atStartOfDay(zone).toInstant()
            val windowEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
            val overlaps = if (span.end <= span.start) {
                span.start >= windowStart && span.start < windowEnd
            } else {
                span.start < windowEnd && span.end > windowStart
            }
            if (overlaps) {
                DayItem.Event(
                    event,
                    calendar,
                    continuesFromPrevious = span.start < windowStart,
                    continuesToNext = span.end > windowEnd,
                )
            } else {
                null
            }
        }
    }

private fun isAllDay(item: DayItem): Boolean = item is DayItem.Event && item.event.span is EventSpan.AllDay

private fun timeOf(item: DayItem, zone: ZoneId): LocalTime = when (item) {
    is DayItem.Task -> item.time
    is DayItem.Event -> {
        val span = item.event.span
        if (span is EventSpan.Timed && !item.continuesFromPrevious) {
            span.start.atZone(zone).toLocalTime()
        } else {
            LocalTime.MIDNIGHT
        }
    }
}

private fun idOf(item: DayItem): String = when (item) {
    is DayItem.Event -> item.event.instanceKey
    is DayItem.Task -> item.task.taskId
}

private fun dayItemOrder(zone: ZoneId): Comparator<DayItem> = Comparator { a, b ->
    val aAllDay = isAllDay(a)
    val bAllDay = isAllDay(b)
    when {
        aAllDay != bAllDay -> if (aAllDay) -1 else 1
        aAllDay -> compareValuesBy(
            a as DayItem.Event,
            b as DayItem.Event,
            { it.calendar?.name.orEmpty().lowercase() },
            { it.sortTitle.lowercase() },
            { it.event.instanceKey },
        )
        else -> compareValuesBy(
            a,
            b,
            { timeOf(it, zone) },
            { if (it is DayItem.Event) 0 else 1 },
            { it.sortTitle.lowercase() },
            { idOf(it) },
        )
    }
}
