package com.simposio.taskeer.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.CalendarRepository
import com.simposio.taskeer.data.repository.DayWindowSource
import com.simposio.taskeer.data.repository.PlannedRepository
import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.domain.calendar.CalendarMode
import com.simposio.taskeer.domain.calendar.CalendarRead
import com.simposio.taskeer.domain.calendar.DateSpan
import com.simposio.taskeer.domain.calendar.DayMarks
import com.simposio.taskeer.domain.calendar.DeviceCalendar
import com.simposio.taskeer.domain.calendar.DeviceEvent
import com.simposio.taskeer.domain.calendar.TaskDue
import com.simposio.taskeer.domain.calendar.buildDays
import com.simposio.taskeer.domain.calendar.dayMarks
import com.simposio.taskeer.domain.calendar.loadSpanFor
import com.simposio.taskeer.domain.calendar.monthGrid
import com.simposio.taskeer.domain.calendar.periodFor
import com.simposio.taskeer.domain.calendar.stepAnchor
import com.simposio.taskeer.domain.date.DayWindow
import com.simposio.taskeer.domain.date.FirstDayOfWeekProvider
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.date.dayWindowAt
import com.simposio.taskeer.ui.permission.CalendarAccess
import com.simposio.taskeer.ui.permission.CalendarAccessTracker
import com.simposio.taskeer.ui.permission.CalendarPermissionPolicy
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val calendarRepository: CalendarRepository,
    plannedRepository: PlannedRepository,
    private val preferences: PreferencesRepository,
    dayWindowSource: DayWindowSource,
    private val timeZone: TimeZoneProvider,
    private val firstDayOfWeek: FirstDayOfWeekProvider,
    isReadGranted: () -> Boolean,
    isWriteGranted: () -> Boolean,
    clock: () -> Instant = Instant::now,
) : ViewModel() {

    private val tracker = CalendarAccessTracker(isReadGranted, isWriteGranted)
    private val initialWindow = dayWindowAt(clock(), timeZone.current())
    private val local = MutableStateFlow(Local(anchor = initialWindow.date))

    // Un solo colector del reloj de ventanas: lo comparten la clave de carga y la construcción del estado.
    private val window: StateFlow<DayWindow> = dayWindowSource.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), initialWindow)

    private val events: Flow<KeyedEvents?> = combine(local, window, tracker.access) { state, _, access ->
        loadKeyOf(state, access)
    }
        .distinctUntilChanged()
        .flatMapLatest<LoadKey, KeyedEvents?> { key ->
            val reads = if (key.canRead) {
                calendarRepository.observeEvents(key.span.start, key.span.endExclusive)
            } else {
                flowOf(CalendarRead.NoPermission)
            }
            reads.map { KeyedEvents(key, it) }
        }
        .onStart { emit(null) }

    private val calendars: Flow<CalendarRead<List<DeviceCalendar>>?> =
        combine(local.map { it.retry }, tracker.access.map { it.read }) { retry, read -> read to retry }
            .distinctUntilChanged()
            .flatMapLatest<Pair<Boolean, Int>, CalendarRead<List<DeviceCalendar>>?> { (read, _) ->
                if (read) calendarRepository.observeCalendars() else flowOf(CalendarRead.NoPermission)
            }
            .onStart { emit(null) }

    private val content: Flow<Content> = combine(
        events,
        calendars,
        plannedRepository.observeDatedTasks(),
        preferences.hiddenCalendarIds,
        ::Content,
    )

    val uiState: StateFlow<CalendarUiState> = combine(
        local,
        window,
        tracker.access,
        tracker.answered,
        content,
    ) { state, window, access, answered, content ->
        compute(state, window, access, answered, content)
    }
        .onEach(::releaseUnresolvedScroll)
        .map { it.state }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), initialState())

    // Aparte del estado completo para que el menú del shell no se recomponga con cada cambio de eventos.
    val canReadCalendars: StateFlow<Boolean> = tracker.access
        .map { it.read }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), tracker.access.value.read)

    fun selectMode(mode: CalendarMode) = local.update { it.copy(mode = mode) }

    fun previous() = local.update { it.copy(anchor = stepAnchor(it.mode, it.anchor, -1)) }

    fun next() = local.update { it.copy(anchor = stepAnchor(it.mode, it.anchor, 1)) }

    fun goToToday() = local.update { it.copy(anchor = window.value.date) }

    fun selectDate(date: LocalDate) = local.update { it.copy(anchor = date, scrollRequest = date) }

    fun consumeScroll() = local.update { it.copy(scrollRequest = null) }

    fun toggleMonth() = local.update { it.copy(monthExpanded = !it.monthExpanded) }

    fun openPicker() = local.update { it.copy(isPickerOpen = true) }

    fun closePicker() = local.update { it.copy(isPickerOpen = false) }

    fun retry() = local.update { it.copy(retry = it.retry + 1) }

    fun currentAnchor(): LocalDate = local.value.anchor

    fun refreshAccess() = tracker.refresh()

    fun onPermissionResult() = tracker.onResult()

    fun setCalendarVisible(id: Long, visible: Boolean) {
        viewModelScope.launch {
            try {
                preferences.setCalendarHidden(id, hidden = !visible)
            } catch (failure: IOException) {
                // Sin escritura el ajuste no cambia y la casilla sigue reflejando el valor guardado.
            }
        }
    }

    private fun loadKeyOf(state: Local, access: CalendarAccess): LoadKey = LoadKey(
        span = loadSpanFor(state.mode, state.anchor, firstDayOfWeek.current()),
        zone = timeZone.current(),
        canRead = access.read,
        retry = state.retry,
    )

    private fun compute(
        state: Local,
        window: DayWindow,
        access: CalendarAccess,
        answered: Boolean,
        content: Content,
    ): Computed {
        val first = firstDayOfWeek.current()
        val key = loadKeyOf(state, access)
        val events = (content.events?.read as? CalendarRead.Ok)?.value.orEmpty()
        val deviceCalendars = (content.calendars as? CalendarRead.Ok)?.value.orEmpty()
        val loaded = buildDays(key.span, events, deviceCalendars, content.hidden, content.tasks, key.zone, includeEmpty = true)
        val period = periodFor(state.mode, state.anchor, first)
        val days = loaded.filter { it.date in period && (state.mode != CalendarMode.AGENDA || it.items.isNotEmpty()) }
        val marks = loaded.associate { it.date to dayMarks(it) }
        val anchorMonth = YearMonth.from(state.anchor)
        val grid = monthGrid(state.anchor, first).map { week ->
            week.map { date ->
                GridCell(
                    date = date,
                    inMonth = YearMonth.from(date) == anchorMonth,
                    isToday = date == window.date,
                    isAnchor = date == state.anchor,
                    marks = marks[date] ?: DayMarks(events = 0, tasks = 0),
                )
            }
        }
        val scrollTarget = state.scrollRequest?.let { requested ->
            if (state.mode == CalendarMode.AGENDA) days.firstOrNull { it.date >= requested }?.date else requested
        }
        return Computed(
            request = state.scrollRequest,
            state = CalendarUiState(
                mode = state.mode,
                anchor = state.anchor,
                today = window.date,
                firstDay = first,
                zoneId = key.zone.id,
                monthExpanded = state.monthExpanded,
                grid = grid,
                days = days,
                calendars = deviceCalendars.map { CalendarOption(it, it.id !in content.hidden) },
                access = access,
                readNotice = CalendarPermissionPolicy.noticeFor(access.read, answered),
                load = loadOf(access, key, content),
                isPickerOpen = state.isPickerOpen,
                scrollTarget = scrollTarget,
            ),
        )
    }

    private fun loadOf(access: CalendarAccess, key: LoadKey, content: Content): CalendarLoad {
        val keyed = content.events
        val calendarsRead = content.calendars
        return when {
            !access.read -> CalendarLoad.Ready
            keyed == null || calendarsRead == null || keyed.key != key -> CalendarLoad.Loading
            keyed.read is CalendarRead.Failed -> CalendarLoad.Failed(keyed.read.error)
            calendarsRead is CalendarRead.Failed -> CalendarLoad.Failed(calendarsRead.error)
            calendarsRead is CalendarRead.Ok && calendarsRead.value.isEmpty() -> CalendarLoad.NoCalendars
            else -> CalendarLoad.Ready
        }
    }

    // Una petición de desplazamiento sin destino (Agenda sin elementos desde esa fecha) no debe quedar viva y
    // saltar más tarde cuando llegue un evento ajeno.
    private fun releaseUnresolvedScroll(computed: Computed) {
        val unresolved = computed.request != null &&
            computed.state.scrollTarget == null &&
            computed.state.load != CalendarLoad.Loading
        if (unresolved) {
            local.update { if (it.scrollRequest == computed.request) it.copy(scrollRequest = null) else it }
        }
    }

    private fun initialState(): CalendarUiState {
        val access = tracker.access.value
        return CalendarUiState(
            anchor = initialWindow.date,
            today = initialWindow.date,
            firstDay = firstDayOfWeek.current(),
            zoneId = timeZone.current().id,
            access = access,
            readNotice = CalendarPermissionPolicy.noticeFor(access.read, answeredThisTransition = false),
            load = if (access.read) CalendarLoad.Loading else CalendarLoad.Ready,
        )
    }

    private data class Local(
        val anchor: LocalDate,
        val mode: CalendarMode = CalendarMode.AGENDA,
        val monthExpanded: Boolean = true,
        val isPickerOpen: Boolean = false,
        val scrollRequest: LocalDate? = null,
        val retry: Int = 0,
    )

    private data class LoadKey(val span: DateSpan, val zone: ZoneId, val canRead: Boolean, val retry: Int)

    private data class KeyedEvents(val key: LoadKey, val read: CalendarRead<List<DeviceEvent>>)

    private data class Content(
        val events: KeyedEvents?,
        val calendars: CalendarRead<List<DeviceCalendar>>?,
        val tasks: List<TaskDue>,
        val hidden: Set<Long>,
    )

    private data class Computed(val state: CalendarUiState, val request: LocalDate?)

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
