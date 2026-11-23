package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.CalendarRepository
import com.simposio.taskeer.domain.calendar.CalendarRead
import com.simposio.taskeer.domain.calendar.DeviceCalendar
import com.simposio.taskeer.domain.calendar.DeviceEvent
import com.simposio.taskeer.domain.calendar.EventDraft
import com.simposio.taskeer.domain.model.DomainResult
import java.time.LocalDate
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart

// emitInitially = false deja las lecturas sin primer valor para probar el estado «cargando».
class FakeCalendarRepository(
    calendars: List<DeviceCalendar> = emptyList(),
    events: List<DeviceEvent> = emptyList(),
    emitInitially: Boolean = true,
) : CalendarRepository {

    private val calendarReads = replayFlow<List<DeviceCalendar>>()
    private val eventReads = replayFlow<List<DeviceEvent>>()

    val eventRequests = mutableListOf<Pair<LocalDate, LocalDate>>()
    var calendarSubscriptions = 0
        private set
    val created = mutableListOf<EventDraft>()
    var createResult: DomainResult<Long> = DomainResult.Success(1L)

    init {
        if (emitInitially) {
            setCalendars(CalendarRead.Ok(calendars))
            setEvents(CalendarRead.Ok(events))
        }
    }

    fun setCalendars(read: CalendarRead<List<DeviceCalendar>>) {
        calendarReads.tryEmit(read)
    }

    fun setEvents(read: CalendarRead<List<DeviceEvent>>) {
        eventReads.tryEmit(read)
    }

    override fun observeCalendars(): Flow<CalendarRead<List<DeviceCalendar>>> =
        calendarReads.onStart { calendarSubscriptions++ }

    override fun observeEvents(from: LocalDate, toExclusive: LocalDate): Flow<CalendarRead<List<DeviceEvent>>> =
        eventReads.onStart { eventRequests.add(from to toExclusive) }

    override suspend fun createEvent(draft: EventDraft): DomainResult<Long> {
        created.add(draft)
        return createResult
    }

    private fun <T> replayFlow() =
        MutableSharedFlow<CalendarRead<T>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
}
