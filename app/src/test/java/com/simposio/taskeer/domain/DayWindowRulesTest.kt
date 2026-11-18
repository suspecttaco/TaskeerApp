package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.date.dayWindowAt
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DayWindowRulesTest {

    private val mexico = ZoneId.of("America/Mexico_City")
    private val madrid = ZoneId.of("Europe/Madrid")
    private val newYork = ZoneId.of("America/New_York")
    private val saoPaulo = ZoneId.of("America/Sao_Paulo")

    @Test
    fun mismoDiaLocal_mismaVentana() {
        val morning = dayWindowAt(Instant.parse("2026-11-17T06:30:00Z"), mexico)
        val night = dayWindowAt(Instant.parse("2026-11-18T05:59:59Z"), mexico)

        assertEquals(morning, night)
        assertEquals(LocalDate.of(2026, 11, 17), morning.date)
        assertEquals(Instant.parse("2026-11-17T06:00:00Z"), morning.start)
    }

    @Test
    fun mismoInstante_enDosZonas_cambiaLaFecha() {
        val instant = Instant.parse("2026-11-17T23:30:00Z")

        assertEquals(LocalDate.of(2026, 11, 17), dayWindowAt(instant, ZoneId.of("UTC")).date)
        assertEquals(LocalDate.of(2026, 11, 18), dayWindowAt(instant, madrid).date)
        assertEquals(LocalDate.of(2026, 11, 17), dayWindowAt(instant, newYork).date)
    }

    @Test
    fun diaConSaltoDeHorario_dura23Horas() {
        val window = dayWindowAt(Instant.parse("2026-03-29T12:00:00Z"), madrid)

        assertEquals(Duration.ofHours(23), Duration.between(window.start, window.end))
    }

    @Test
    fun diaConFinDeHorario_dura25Horas() {
        val window = dayWindowAt(Instant.parse("2026-10-25T12:00:00Z"), madrid)

        assertEquals(Duration.ofHours(25), Duration.between(window.start, window.end))
    }

    @Test
    fun medianocheInexistente_empiezaEnElPrimerInstanteValido() {
        val window = dayWindowAt(Instant.parse("2018-11-04T12:00:00Z"), saoPaulo)

        assertEquals(Instant.parse("2018-11-04T03:00:00Z"), window.start)
        assertEquals(Duration.ofHours(23), Duration.between(window.start, window.end))
    }

    @Test
    fun finDeVentana_perteneceAlDiaSiguiente() {
        val today = dayWindowAt(Instant.parse("2026-11-17T12:00:00Z"), newYork)

        val atEnd = dayWindowAt(today.end, newYork)
        val beforeEnd = dayWindowAt(today.end.minusMillis(1), newYork)

        assertEquals(LocalDate.of(2026, 11, 18), atEnd.date)
        assertEquals(today.end, atEnd.start)
        assertEquals(today, beforeEnd)
    }
}
