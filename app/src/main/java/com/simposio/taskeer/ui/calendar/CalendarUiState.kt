package com.simposio.taskeer.ui.calendar

import com.simposio.taskeer.domain.calendar.CalendarDay
import com.simposio.taskeer.domain.calendar.CalendarMode
import com.simposio.taskeer.domain.calendar.DayMarks
import com.simposio.taskeer.domain.calendar.DeviceCalendar
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.ui.permission.CalendarAccess
import com.simposio.taskeer.ui.permission.CalendarNotice
import java.time.DayOfWeek
import java.time.LocalDate

data class GridCell(
    val date: LocalDate,
    val inMonth: Boolean,
    val isToday: Boolean,
    val isAnchor: Boolean,
    val marks: DayMarks,
)

data class CalendarOption(val calendar: DeviceCalendar, val visible: Boolean)

sealed interface CalendarLoad {
    data object Loading : CalendarLoad

    data object Ready : CalendarLoad

    data object NoCalendars : CalendarLoad

    data class Failed(val error: DomainError) : CalendarLoad
}

data class CalendarUiState(
    val mode: CalendarMode = CalendarMode.AGENDA,
    val anchor: LocalDate,
    val today: LocalDate,
    val firstDay: DayOfWeek,
    val zoneId: String,
    val monthExpanded: Boolean = true,
    val grid: List<List<GridCell>> = emptyList(),
    val days: List<CalendarDay> = emptyList(),
    val calendars: List<CalendarOption> = emptyList(),
    val access: CalendarAccess = CalendarAccess(read = false, write = false),
    val readNotice: CalendarNotice? = null,
    val load: CalendarLoad = CalendarLoad.Loading,
    val isPickerOpen: Boolean = false,
    val scrollTarget: LocalDate? = null,
)
