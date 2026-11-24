package com.simposio.taskeer.domain.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private const val GRID_WEEKS = 6
private const val DAYS_PER_WEEK = 7
private const val THREE_DAYS = 3L

data class DateSpan(val start: LocalDate, val endExclusive: LocalDate) {
    val days: List<LocalDate>
        get() = generateSequence(start) { it.plusDays(1) }.takeWhile { it < endExclusive }.toList()

    operator fun contains(date: LocalDate): Boolean = date >= start && date < endExclusive
}

fun weekStart(date: LocalDate, first: DayOfWeek): LocalDate = date.with(TemporalAdjusters.previousOrSame(first))

fun periodFor(mode: CalendarMode, anchor: LocalDate, first: DayOfWeek): DateSpan = when (mode) {
    CalendarMode.DAY -> DateSpan(anchor, anchor.plusDays(1))
    CalendarMode.THREE_DAYS -> DateSpan(anchor, anchor.plusDays(THREE_DAYS))
    CalendarMode.WEEK -> weekStart(anchor, first).let { DateSpan(it, it.plusDays(DAYS_PER_WEEK.toLong())) }
    CalendarMode.AGENDA -> anchor.withDayOfMonth(1).let { DateSpan(it, it.plusMonths(1)) }
}

// java.time recorta el día al fin de mes: 31 ene + 1 mes = 28/29 feb.
fun stepAnchor(mode: CalendarMode, anchor: LocalDate, direction: Int): LocalDate {
    val sign = if (direction < 0) -1L else 1L
    return when (mode) {
        CalendarMode.DAY -> anchor.plusDays(sign)
        CalendarMode.THREE_DAYS -> anchor.plusDays(sign * THREE_DAYS)
        CalendarMode.WEEK -> anchor.plusDays(sign * DAYS_PER_WEEK)
        CalendarMode.AGENDA -> anchor.plusMonths(sign)
    }
}

fun monthGrid(anchor: LocalDate, first: DayOfWeek): List<List<LocalDate>> {
    val gridStart = weekStart(anchor.withDayOfMonth(1), first)
    return (0 until GRID_WEEKS).map { week ->
        (0 until DAYS_PER_WEEK).map { day -> gridStart.plusDays((week * DAYS_PER_WEEK + day).toLong()) }
    }
}

fun weekdayHeader(first: DayOfWeek): List<DayOfWeek> = (0 until DAYS_PER_WEEK).map { first.plus(it.toLong()) }

// Rango que se consulta: cubre la rejilla y el periodo aunque «3 días» cruce el final de la rejilla.
fun loadSpanFor(mode: CalendarMode, anchor: LocalDate, first: DayOfWeek): DateSpan {
    val grid = monthGrid(anchor, first)
    val gridStart = grid.first().first()
    val gridEnd = grid.last().last().plusDays(1)
    val period = periodFor(mode, anchor, first)
    return DateSpan(minOf(gridStart, period.start), maxOf(gridEnd, period.endExclusive))
}
