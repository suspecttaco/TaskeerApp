package com.simposio.taskeer.domain.calendar

import com.simposio.taskeer.domain.model.DomainError
import java.time.Instant
import java.time.LocalDate

enum class CalendarMode { AGENDA, DAY, THREE_DAYS, WEEK }

data class DeviceCalendar(
    val id: Long,
    val name: String,
    val accountName: String,
    val colorArgb: Int,
    val isWritable: Boolean,
)

sealed interface EventSpan {
    data class Timed(val start: Instant, val end: Instant) : EventSpan

    // Fechas del proveedor (UTC), independientes de la zona. endExclusive <= start se trata como un día.
    data class AllDay(val start: LocalDate, val endExclusive: LocalDate) : EventSpan
}

// instanceKey es único por ocurrencia: un evento recurrente comparte eventId entre instancias.
data class DeviceEvent(
    val instanceKey: String,
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val span: EventSpan,
)

data class EventDraft(val title: String, val calendarId: Long?, val span: EventSpan)

sealed interface CalendarRead<out T> {
    data class Ok<T>(val value: T) : CalendarRead<T>

    data object NoPermission : CalendarRead<Nothing>

    data class Failed(val error: DomainError) : CalendarRead<Nothing>
}
