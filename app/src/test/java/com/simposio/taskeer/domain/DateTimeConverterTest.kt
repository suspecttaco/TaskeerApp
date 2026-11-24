package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DateTimeConverterTest {

    private val madrid = DateTimeConverter(TimeZoneProvider { ZoneId.of("Europe/Madrid") })

    @Test
    fun idaYVuelta_preservaElInstante() {
        val utc = Instant.parse("2026-10-13T08:15:30Z")

        val result = madrid.toInstant(madrid.toLocalDateTime(utc))

        assertEquals(utc, result)
    }

    @Test
    fun localEnLaZonaDelUsuario_seConvierteEnUtc() {
        val local = LocalDateTime.of(2026, 10, 13, 12, 30)

        val utc = madrid.toInstant(local)

        assertEquals(Instant.parse("2026-10-13T10:30:00Z"), utc)
        assertEquals(local, madrid.toLocalDateTime(utc))
    }

    @Test
    fun conversionConocida_cruzaElCambioDeDia() {
        val utc = Instant.parse("2026-10-13T23:30:00Z")

        val local = madrid.toLocalDateTime(utc)

        assertEquals(LocalDateTime.of(2026, 10, 14, 1, 30), local)
        assertEquals(LocalDate.of(2026, 10, 14), local.toLocalDate())
    }

    @Test
    fun mismoInstante_horaLocalDistintaSegunZona() {
        val utc = Instant.parse("2026-11-17T12:00:00Z")
        var zone = ZoneId.of("America/Mexico_City")
        val converter = DateTimeConverter { zone }

        val mexico = converter.toLocalDateTime(utc)
        zone = ZoneId.of("Asia/Tokyo")
        val tokyo = converter.toLocalDateTime(utc)

        assertEquals(LocalDateTime.of(2026, 11, 17, 6, 0), mexico)
        assertEquals(LocalDateTime.of(2026, 11, 17, 21, 0), tokyo)
    }
}
