package com.simposio.taskeer.domain.calendar

import com.simposio.taskeer.domain.model.DomainError
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

const val EVENT_TITLE_MAX = 200

private val DEFAULT_START_TIME: LocalTime = LocalTime.of(9, 0)
private const val DEFAULT_DURATION_HOURS = 1L

fun validateEventDraft(draft: EventDraft, calendars: List<DeviceCalendar>): DomainError? {
    val title = draft.title.trim()
    return when {
        title.isEmpty() -> validation("EVENT_TITLE_REQUIRED", "El evento necesita un título")
        title.length > EVENT_TITLE_MAX ->
            validation("EVENT_TITLE_TOO_LONG", "El título no puede superar $EVENT_TITLE_MAX caracteres")
        endsBeforeStart(draft.span) ->
            validation("EVENT_END_BEFORE_START", "El fin debe ser posterior al inicio")
        draft.calendarId == null -> validation("EVENT_CALENDAR_REQUIRED", "Elige un calendario")
        calendars.none { it.id == draft.calendarId && it.isWritable } ->
            validation("EVENT_CALENDAR_NOT_WRITABLE", "Ese calendario no permite crear eventos")
        else -> null
    }
}

private fun validation(code: String, message: String) = DomainError.Validation(code, message)

private fun endsBeforeStart(span: EventSpan): Boolean = when (span) {
    is EventSpan.Timed -> span.end <= span.start
    is EventSpan.AllDay -> span.endExclusive <= span.start
}

// Si la próxima hora en punto cae en el día siguiente (23:xx), el borrador empieza a las 00:00 de ese día.
fun defaultEventDraft(anchor: LocalDate, now: Instant, zone: ZoneId, calendarId: Long?): EventDraft {
    val local = now.atZone(zone).toLocalDateTime()
    val start = if (local.toLocalDate() == anchor) {
        local.truncatedTo(ChronoUnit.HOURS).plusHours(1)
    } else {
        LocalDateTime.of(anchor, DEFAULT_START_TIME)
    }
    return EventDraft(title = "", calendarId = calendarId, span = timedSpan(start, zone))
}

private fun timedSpan(start: LocalDateTime, zone: ZoneId): EventSpan.Timed = EventSpan.Timed(
    start = start.atZone(zone).toInstant(),
    end = start.plusHours(DEFAULT_DURATION_HOURS).atZone(zone).toInstant(),
)

// El fin conserva la duración real (en instantes), también al cruzar un cambio de hora.
fun withStart(draft: EventDraft, newStart: LocalDateTime, zone: ZoneId): EventDraft = when (val span = draft.span) {
    is EventSpan.Timed -> {
        val start = newStart.atZone(zone).toInstant()
        draft.copy(span = EventSpan.Timed(start, start.plus(Duration.between(span.start, span.end))))
    }
    is EventSpan.AllDay -> {
        val start = newStart.toLocalDate()
        val length = maxOf(ChronoUnit.DAYS.between(span.start, span.endExclusive), 1L)
        draft.copy(span = EventSpan.AllDay(start, start.plusDays(length)))
    }
}

fun withEnd(draft: EventDraft, newEnd: LocalDateTime, zone: ZoneId): EventDraft = when (val span = draft.span) {
    is EventSpan.Timed -> draft.copy(span = span.copy(end = newEnd.atZone(zone).toInstant()))
    is EventSpan.AllDay -> draft.copy(span = span.copy(endExclusive = newEnd.toLocalDate().plusDays(1)))
}

fun withAllDay(draft: EventDraft, allDay: Boolean, zone: ZoneId): EventDraft = when (val span = draft.span) {
    is EventSpan.Timed ->
        if (allDay) {
            val start = span.start.atZone(zone).toLocalDate()
            val lastDay = maxOf(span.end.atZone(zone).toLocalDate(), start)
            draft.copy(span = EventSpan.AllDay(start, lastDay.plusDays(1)))
        } else {
            draft
        }
    is EventSpan.AllDay ->
        if (allDay) draft else draft.copy(span = timedSpan(LocalDateTime.of(span.start, DEFAULT_START_TIME), zone))
}

fun defaultCalendarId(calendars: List<DeviceCalendar>, hiddenIds: Set<Long>): Long? = calendars
    .sortedWith(compareBy({ it.accountName.lowercase() }, { it.name.lowercase() }))
    .firstOrNull { it.isWritable && it.id !in hiddenIds }
    ?.id

fun eventStartLocal(draft: EventDraft, zone: ZoneId): LocalDateTime = when (val span = draft.span) {
    is EventSpan.Timed -> span.start.atZone(zone).toLocalDateTime()
    is EventSpan.AllDay -> span.start.atStartOfDay()
}

// En todo el día el fin que se muestra es el último día incluido (endExclusive - 1), como en los calendarios habituales.
fun eventEndLocal(draft: EventDraft, zone: ZoneId): LocalDateTime = when (val span = draft.span) {
    is EventSpan.Timed -> span.end.atZone(zone).toLocalDateTime()
    is EventSpan.AllDay -> span.endExclusive.minusDays(1).atStartOfDay()
}

fun eventStartDate(draft: EventDraft, zone: ZoneId): LocalDate = eventStartLocal(draft, zone).toLocalDate()
