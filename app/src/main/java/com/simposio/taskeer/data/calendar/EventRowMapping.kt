package com.simposio.taskeer.data.calendar

import com.simposio.taskeer.domain.calendar.EventSpan
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private const val OPAQUE_ALPHA: Int = 0xFF shl 24

// El proveedor guarda los eventos de todo el día a medianoche UTC: se leen como fechas, sin pasar por la zona local.
fun eventSpanOf(beginMillis: Long, endMillis: Long, allDay: Boolean): EventSpan =
    if (allDay) {
        EventSpan.AllDay(utcDateOf(beginMillis), utcDateOf(endMillis))
    } else {
        EventSpan.Timed(Instant.ofEpochMilli(beginMillis), Instant.ofEpochMilli(endMillis))
    }

fun utcMidnightMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun eventInstanceKey(eventId: Long, beginMillis: Long): String = "$eventId:$beginMillis"

// Algunas cuentas guardan el color sin canal alfa; se fuerza opaco para que no salga transparente.
fun calendarColor(raw: Int): Int = raw or OPAQUE_ALPHA

private fun utcDateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
