package com.simposio.taskeer.ui.calendar

import com.simposio.taskeer.domain.calendar.DeviceCalendar
import com.simposio.taskeer.domain.calendar.EventDraft
import com.simposio.taskeer.domain.calendar.EventSpan
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.ui.permission.CalendarAccess
import com.simposio.taskeer.ui.permission.CalendarNotice
import java.time.Instant
import java.time.LocalDate

enum class EventPanel { Form, StartDate, StartTime, EndDate, EndTime, Calendars }

data class EventEditorUiState(
    val isOpen: Boolean = false,
    val isClosing: Boolean = false,
    val panel: EventPanel = EventPanel.Form,
    val draft: EventDraft = EMPTY_DRAFT,
    val zoneId: String = "UTC",
    val calendars: List<DeviceCalendar> = emptyList(),
    val hiddenIds: Set<Long> = emptySet(),
    val access: CalendarAccess = CalendarAccess(read = false, write = false),
    val writeNotice: CalendarNotice? = null,
    val titleTouched: Boolean = false,
    val validation: DomainError? = null,
    val isSaving: Boolean = false,
    val error: DomainError? = null,
    val createdDate: LocalDate? = null,
) {
    val hasWritableCalendar: Boolean get() = calendars.any { it.isWritable }

    val canSave: Boolean get() = access.write && validation == null && !isSaving && hasWritableCalendar
}

internal val EMPTY_DRAFT = EventDraft(title = "", calendarId = null, span = EventSpan.Timed(Instant.EPOCH, Instant.EPOCH))
