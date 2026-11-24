package com.simposio.taskeer.ui.calendar

import com.simposio.taskeer.domain.calendar.CalendarDay
import com.simposio.taskeer.domain.calendar.CalendarMode
import com.simposio.taskeer.domain.calendar.DayItem
import java.time.LocalDate

// La clave de cada fila es estable por contenido (fecha, instancia o id): un evento multidía aparece una vez por día.
sealed interface BodyRow {
    val key: String

    data class DayHeader(val date: LocalDate, val isToday: Boolean) : BodyRow {
        override val key: String get() = "d:$date"
    }

    data class EventEntry(val item: DayItem.Event, val date: LocalDate) : BodyRow {
        override val key: String get() = "e:${item.event.instanceKey}:$date"
    }

    data class TaskEntry(val item: DayItem.Task, val date: LocalDate) : BodyRow {
        override val key: String get() = "t:${item.task.taskId}:$date"
    }

    data class EmptyDay(val date: LocalDate) : BodyRow {
        override val key: String get() = "x:$date"
    }

    data object EmptyMonth : BodyRow {
        override val key: String get() = "empty-month"
    }
}

fun bodyRows(days: List<CalendarDay>, mode: CalendarMode, today: LocalDate): List<BodyRow> {
    if (mode == CalendarMode.AGENDA && days.isEmpty()) return listOf(BodyRow.EmptyMonth)
    return days.flatMap { day ->
        val header = BodyRow.DayHeader(day.date, isToday = day.date == today)
        val entries = day.items.map { item ->
            when (item) {
                is DayItem.Event -> BodyRow.EventEntry(item, day.date)
                is DayItem.Task -> BodyRow.TaskEntry(item, day.date)
            }
        }
        listOf(header) + entries.ifEmpty { listOf(BodyRow.EmptyDay(day.date)) }
    }
}

fun List<BodyRow>.indexOfDay(date: LocalDate): Int? =
    indexOfFirst { it is BodyRow.DayHeader && it.date == date }.takeIf { it >= 0 }
