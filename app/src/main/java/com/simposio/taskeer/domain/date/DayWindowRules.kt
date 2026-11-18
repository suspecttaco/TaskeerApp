package com.simposio.taskeer.domain.date

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayWindow(val date: LocalDate, val start: Instant, val end: Instant)

// atStartOfDay resuelve el cambio de horario: una medianoche inexistente empieza en el primer instante válido.
fun dayWindowAt(now: Instant, zone: ZoneId): DayWindow {
    val date = now.atZone(zone).toLocalDate()
    return DayWindow(
        date = date,
        start = date.atStartOfDay(zone).toInstant(),
        end = date.plusDays(1).atStartOfDay(zone).toInstant(),
    )
}
