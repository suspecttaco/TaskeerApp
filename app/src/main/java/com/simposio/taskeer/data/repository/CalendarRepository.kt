package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.calendar.CalendarRead
import com.simposio.taskeer.domain.calendar.DeviceCalendar
import com.simposio.taskeer.domain.calendar.DeviceEvent
import com.simposio.taskeer.domain.calendar.EventDraft
import com.simposio.taskeer.domain.model.DomainResult
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

// Cada observe* emite al suscribirse y tras cada cambio del proveedor. Sin permiso de lectura emite NoPermission
// sin tocar el proveedor: quien observa se vuelve a suscribir cuando cambia el permiso.
interface CalendarRepository {
    fun observeCalendars(): Flow<CalendarRead<List<DeviceCalendar>>>

    fun observeEvents(from: LocalDate, toExclusive: LocalDate): Flow<CalendarRead<List<DeviceEvent>>>

    suspend fun createEvent(draft: EventDraft): DomainResult<Long>
}
