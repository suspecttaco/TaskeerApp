package com.simposio.taskeer.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.calendar.CalendarMode
import com.simposio.taskeer.domain.calendar.DateSpan
import com.simposio.taskeer.domain.calendar.DayItem
import com.simposio.taskeer.domain.calendar.DayMarks
import com.simposio.taskeer.domain.calendar.EventSpan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

internal fun formatTime(time: LocalTime, locale: Locale): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(time)

internal fun formatPattern(date: LocalDate, pattern: String, locale: Locale): String =
    DateTimeFormatter.ofPattern(pattern, locale).format(date)

@Composable
internal fun fullDateText(date: LocalDate): String =
    formatPattern(date, stringResource(R.string.calendar_date_pattern), currentLocale())

@Composable
internal fun periodTitle(mode: CalendarMode, period: DateSpan, anchor: LocalDate): String {
    val locale = currentLocale()
    return when (mode) {
        CalendarMode.AGENDA -> formatPattern(anchor, stringResource(R.string.calendar_month_pattern), locale)
            .replaceFirstChar { it.titlecase(locale) }
        CalendarMode.DAY -> fullDateText(period.start).replaceFirstChar { it.titlecase(locale) }
        CalendarMode.THREE_DAYS, CalendarMode.WEEK -> {
            val pattern = stringResource(R.string.calendar_range_pattern)
            stringResource(
                R.string.calendar_range_title,
                formatPattern(period.start, pattern, locale),
                formatPattern(period.endExclusive.minusDays(1), pattern, locale),
            )
        }
    }
}

@Composable
internal fun weekdayInitial(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.NARROW, currentLocale()).uppercase(currentLocale())

@Composable
internal fun cellDescription(date: LocalDate, marks: DayMarks, isToday: Boolean): String {
    val parts = buildList {
        add(fullDateText(date))
        if (isToday) add(stringResource(R.string.calendar_cell_today))
        if (marks.events > 0) add(pluralStringResource(R.plurals.calendar_cell_events, marks.events, marks.events))
        if (marks.tasks > 0) add(pluralStringResource(R.plurals.calendar_cell_tasks, marks.tasks, marks.tasks))
        if (marks.events == 0 && marks.tasks == 0) add(stringResource(R.string.calendar_cell_nothing))
    }
    return parts.joinToString(", ")
}

@Composable
internal fun eventTimeText(item: DayItem.Event, zone: ZoneId): String {
    val locale = currentLocale()
    return when (val span = item.event.span) {
        is EventSpan.AllDay -> stringResource(R.string.calendar_all_day)
        is EventSpan.Timed -> {
            val start = formatTime(span.start.atZone(zone).toLocalTime(), locale)
            val end = formatTime(span.end.atZone(zone).toLocalTime(), locale)
            when {
                item.continuesFromPrevious && item.continuesToNext -> stringResource(R.string.calendar_time_both)
                item.continuesFromPrevious -> stringResource(R.string.calendar_time_continued, end)
                item.continuesToNext -> stringResource(R.string.calendar_time_continues, start)
                span.end <= span.start -> start
                else -> stringResource(R.string.calendar_time_range, start, end)
            }
        }
    }
}
