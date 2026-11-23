package com.simposio.taskeer.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.CalendarRepository
import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.domain.calendar.CalendarRead
import com.simposio.taskeer.domain.calendar.DeviceCalendar
import com.simposio.taskeer.domain.calendar.EventDraft
import com.simposio.taskeer.domain.calendar.defaultCalendarId
import com.simposio.taskeer.domain.calendar.defaultEventDraft
import com.simposio.taskeer.domain.calendar.eventEndLocal
import com.simposio.taskeer.domain.calendar.eventStartDate
import com.simposio.taskeer.domain.calendar.eventStartLocal
import com.simposio.taskeer.domain.calendar.validateEventDraft
import com.simposio.taskeer.domain.calendar.withAllDay
import com.simposio.taskeer.domain.calendar.withEnd
import com.simposio.taskeer.domain.calendar.withStart
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.ui.permission.CalendarAccessTracker
import com.simposio.taskeer.ui.permission.CalendarPermissionPolicy
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EventEditorViewModel(
    private val calendarRepository: CalendarRepository,
    private val preferences: PreferencesRepository,
    isReadGranted: () -> Boolean,
    isWriteGranted: () -> Boolean,
    private val timeZone: TimeZoneProvider,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {

    private val tracker = CalendarAccessTracker(isReadGranted, isWriteGranted)
    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<EventEditorUiState> = combine(
        local,
        tracker.access,
        tracker.answered,
        preferences.hiddenCalendarIds,
    ) { state, access, answered, hidden ->
        EventEditorUiState(
            isOpen = state.isOpen,
            isClosing = state.isClosing,
            panel = state.panel,
            draft = state.draft,
            zoneId = timeZone.current().id,
            calendars = state.calendars,
            hiddenIds = hidden,
            access = access,
            writeNotice = CalendarPermissionPolicy.noticeFor(access.write, answered),
            titleTouched = state.titleTouched,
            validation = if (state.isOpen) validateEventDraft(state.draft, state.calendars) else null,
            isSaving = state.isSaving,
            error = state.error,
            createdDate = state.createdDate,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), EventEditorUiState())

    fun open(anchor: LocalDate) {
        tracker.refresh()
        local.value = Local(
            isOpen = true,
            draft = defaultEventDraft(anchor, clock(), timeZone.current(), calendarId = null),
        )
        loadCalendars()
    }

    fun onTitleChange(title: String) = editDraft(touchTitle = true) { it.copy(title = title) }

    fun onAllDayChange(allDay: Boolean) = editDraft { withAllDay(it, allDay, timeZone.current()) }

    fun openPanel(panel: EventPanel) = local.update { it.copy(panel = panel) }

    fun onPickStartDate(date: LocalDate) = editDraft(closePanel = true) {
        withStart(it, LocalDateTime.of(date, eventStartLocal(it, timeZone.current()).toLocalTime()), timeZone.current())
    }

    fun onPickStartTime(time: LocalTime) = editDraft(closePanel = true) {
        withStart(it, LocalDateTime.of(eventStartLocal(it, timeZone.current()).toLocalDate(), time), timeZone.current())
    }

    fun onPickEndDate(date: LocalDate) = editDraft(closePanel = true) {
        withEnd(it, LocalDateTime.of(date, eventEndLocal(it, timeZone.current()).toLocalTime()), timeZone.current())
    }

    fun onPickEndTime(time: LocalTime) = editDraft(closePanel = true) {
        withEnd(it, LocalDateTime.of(eventEndLocal(it, timeZone.current()).toLocalDate(), time), timeZone.current())
    }

    fun onCalendarChosen(calendarId: Long) = editDraft(closePanel = true) { it.copy(calendarId = calendarId) }

    fun refreshAccess() {
        tracker.refresh()
        reloadCalendarsIfMissing()
    }

    fun onPermissionResult() {
        tracker.onResult()
        reloadCalendarsIfMissing()
    }

    fun save() {
        val state = local.value
        if (!state.isOpen || state.isSaving || !tracker.access.value.write) return
        if (validateEventDraft(state.draft, state.calendars) != null) return
        local.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = calendarRepository.createEvent(state.draft)) {
                is DomainResult.Success -> local.update {
                    it.copy(
                        isSaving = false,
                        isClosing = true,
                        createdDate = eventStartDate(state.draft, timeZone.current()),
                    )
                }
                is DomainResult.Failure -> local.update { it.copy(isSaving = false, error = result.error) }
            }
        }
    }

    fun onBack() {
        if (local.value.panel == EventPanel.Form) close() else local.update { it.copy(panel = EventPanel.Form) }
    }

    // Cierre con animación de la hoja: la hoja llama a dismiss() cuando termina de ocultarse.
    fun close() = local.update { if (it.isOpen) it.copy(isClosing = true) else it }

    // Cierre inmediato, sin descartar la fecha del evento recién creado que el shell aún no ha consumido.
    fun dismiss() = local.update { Local(createdDate = it.createdDate) }

    fun consumeCreated() = local.update { it.copy(createdDate = null) }

    private fun editDraft(
        touchTitle: Boolean = false,
        closePanel: Boolean = false,
        transform: (EventDraft) -> EventDraft,
    ) = local.update {
        it.copy(
            draft = transform(it.draft),
            panel = if (closePanel) EventPanel.Form else it.panel,
            titleTouched = it.titleTouched || touchTitle,
            error = null,
        )
    }

    private fun reloadCalendarsIfMissing() {
        val state = local.value
        if (state.isOpen && state.calendars.isEmpty()) loadCalendars()
    }

    private fun loadCalendars() {
        viewModelScope.launch {
            val read = tracker.access.value.read
            val calendars = if (read) {
                (calendarRepository.observeCalendars().first() as? CalendarRead.Ok)?.value.orEmpty()
            } else {
                emptyList()
            }
            val hidden = preferences.hiddenCalendarIds.first()
            local.update { state ->
                if (!state.isOpen) return@update state
                val calendarId = state.draft.calendarId ?: defaultCalendarId(calendars, hidden)
                state.copy(calendars = calendars, draft = state.draft.copy(calendarId = calendarId))
            }
        }
    }

    private data class Local(
        val isOpen: Boolean = false,
        val isClosing: Boolean = false,
        val panel: EventPanel = EventPanel.Form,
        val draft: EventDraft = EMPTY_DRAFT,
        val calendars: List<DeviceCalendar> = emptyList(),
        val titleTouched: Boolean = false,
        val isSaving: Boolean = false,
        val error: DomainError? = null,
        val createdDate: LocalDate? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
