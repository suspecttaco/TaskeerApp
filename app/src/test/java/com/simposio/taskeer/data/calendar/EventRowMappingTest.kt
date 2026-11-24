package com.simposio.taskeer.data.calendar

import com.simposio.taskeer.domain.calendar.EventSpan
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class EventRowMappingTest {

    private val begin = Instant.parse("2026-11-20T10:00:00Z").toEpochMilli()
    private val end = Instant.parse("2026-11-20T11:00:00Z").toEpochMilli()

    @Test
    fun eventSpanOf_conHora_conservaLosInstantes() {
        assertEquals(
            EventSpan.Timed(Instant.parse("2026-11-20T10:00:00Z"), Instant.parse("2026-11-20T11:00:00Z")),
            eventSpanOf(begin, end, allDay = false),
        )
    }

    @Test
    fun eventSpanOf_todoElDia_leeLaMedianocheUtcComoFecha() {
        val start = Instant.parse("2026-11-20T00:00:00Z").toEpochMilli()
        val endExclusive = Instant.parse("2026-11-21T00:00:00Z").toEpochMilli()

        assertEquals(
            EventSpan.AllDay(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 21)),
            eventSpanOf(start, endExclusive, allDay = true),
        )
    }

    @Test
    fun eventSpanOf_todoElDia_noDependeDeLaZonaPorDefecto() {
        val start = Instant.parse("2026-11-20T00:00:00Z").toEpochMilli()
        val endExclusive = Instant.parse("2026-11-23T00:00:00Z").toEpochMilli()
        val original = TimeZone.getDefault()
        try {
            listOf("America/Los_Angeles", "UTC", "Pacific/Auckland").forEach { id ->
                TimeZone.setDefault(TimeZone.getTimeZone(id))

                assertEquals(
                    id,
                    EventSpan.AllDay(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 23)),
                    eventSpanOf(start, endExclusive, allDay = true),
                )
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun utcMidnightMillis_esLaMedianocheUtcDeLaFecha() {
        assertEquals(
            Instant.parse("2026-11-20T00:00:00Z").toEpochMilli(),
            utcMidnightMillis(LocalDate.of(2026, 11, 20)),
        )
    }

    @Test
    fun eventInstanceKey_distingueInstanciasDelMismoEvento() {
        assertEquals("7:1000", eventInstanceKey(7, 1000))
        assertEquals(false, eventInstanceKey(7, 1000) == eventInstanceKey(7, 2000))
    }

    @Test
    fun calendarColor_fuerzaElCanalAlfaOpaco() {
        assertEquals(0xFF123456.toInt(), calendarColor(0x00123456))
        assertEquals(0xFF123456.toInt(), calendarColor(0xFF123456.toInt()))
    }
}
