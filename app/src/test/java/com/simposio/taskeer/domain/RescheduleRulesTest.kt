package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.task.RescheduleTarget
import com.simposio.taskeer.domain.task.dueForQuickAdd
import com.simposio.taskeer.domain.task.rescheduledDue
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RescheduleRulesTest {

    private val today = LocalDate.of(2026, 11, 17)

    @Test
    fun manana_conservaLaHora() {
        val current = LocalDateTime.of(2026, 11, 10, 18, 30)

        assertEquals(
            LocalDateTime.of(2026, 11, 18, 18, 30),
            rescheduledDue(current, RescheduleTarget.TOMORROW, today),
        )
    }

    @Test
    fun manana_sinFecha_usa09() {
        assertEquals(
            LocalDateTime.of(2026, 11, 18, 9, 0),
            rescheduledDue(null, RescheduleTarget.TOMORROW, today),
        )
    }

    @Test
    fun proximaSemana_sumaSieteDias() {
        val current = LocalDateTime.of(2026, 11, 17, 7, 15)

        assertEquals(
            LocalDateTime.of(2026, 11, 24, 7, 15),
            rescheduledDue(current, RescheduleTarget.NEXT_WEEK, today),
        )
    }

    @Test
    fun sinFecha_devuelveNulo() {
        assertNull(rescheduledDue(LocalDateTime.of(2026, 11, 17, 7, 15), RescheduleTarget.SOMEDAY, today))
    }

    @Test
    fun alCruzarCambioDeHorario_conservaLaHoraLocal() {
        val converter = DateTimeConverter(TimeZoneProvider { ZoneId.of("America/New_York") })
        val current = LocalDateTime.of(2026, 10, 31, 9, 0)

        val due = rescheduledDue(current, RescheduleTarget.TOMORROW, LocalDate.of(2026, 10, 31))!!

        assertEquals(LocalTime.of(9, 0), due.toLocalTime())
        assertEquals(Instant.parse("2026-11-01T14:00:00Z"), converter.toInstant(due))
    }

    @Test
    fun madrid_proximaSemana_conservaLaHoraLocalTrasElCambioDeOtono() {
        val converter = DateTimeConverter(TimeZoneProvider { ZoneId.of("Europe/Madrid") })
        val current = LocalDateTime.of(2026, 10, 20, 10, 0)

        val due = rescheduledDue(current, RescheduleTarget.NEXT_WEEK, LocalDate.of(2026, 10, 27))!!

        assertEquals(Instant.parse("2026-11-03T09:00:00Z"), converter.toInstant(due))
    }

    @Test
    fun altaRapida_sinHora_usa09() {
        assertEquals(LocalDateTime.of(2026, 11, 17, 9, 0), dueForQuickAdd(today, null))
    }

    @Test
    fun altaRapida_conHora_laRespeta() {
        assertEquals(LocalDateTime.of(2026, 11, 17, 14, 45), dueForQuickAdd(today, LocalTime.of(14, 45)))
    }

    @Test
    fun altaRapida_sinFecha_devuelveNulo() {
        assertNull(dueForQuickAdd(null, LocalTime.of(14, 45)))
    }
}
