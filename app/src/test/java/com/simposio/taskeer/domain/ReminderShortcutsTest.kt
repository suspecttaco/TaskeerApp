package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.reminder.laterToday
import com.simposio.taskeer.domain.reminder.nextWeekAt
import com.simposio.taskeer.domain.reminder.tomorrowAt
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderShortcutsTest {

    private val madrid: ZoneId = ZoneId.of("Europe/Madrid")
    private val mexico: ZoneId = ZoneId.of("America/Mexico_City")
    private val nine: LocalTime = LocalTime.of(9, 0)

    @Test
    fun laterToday_sumaCincoHorasAbsolutasYPuedeCruzarLaMedianoche() {
        val now = Instant.parse("2026-11-04T21:30:00Z")

        assertEquals(Instant.parse("2026-11-05T02:30:00Z"), laterToday(now))
    }

    @Test
    fun tomorrowAt_usaLaFechaLocalDeLaZonaYNoLaUtc() {
        val now = Instant.parse("2026-11-05T02:00:00Z")

        assertEquals(Instant.parse("2026-11-05T15:00:00Z"), tomorrowAt(now, mexico, nine))
        assertEquals(Instant.parse("2026-11-06T08:00:00Z"), tomorrowAt(now, madrid, nine))
    }

    @Test
    fun tomorrowAt_usaLaHoraPorDefectoRecibida() {
        val now = Instant.parse("2026-11-04T10:00:00Z")

        assertEquals(
            Instant.parse("2026-11-05T07:30:00Z"),
            tomorrowAt(now, madrid, LocalTime.of(8, 30)),
        )
    }

    @Test
    fun tomorrowAt_conHoraInexistenteEnSaltoDeVerano_devuelveElInstantePosterior() {
        val now = Instant.parse("2026-03-28T10:00:00Z")

        assertEquals(
            Instant.parse("2026-03-29T01:30:00Z"),
            tomorrowAt(now, madrid, LocalTime.of(2, 30)),
        )
    }

    @Test
    fun tomorrowAt_conHoraExistenteDosVeces_devuelveElPrimero() {
        val now = Instant.parse("2026-10-24T10:00:00Z")

        assertEquals(
            Instant.parse("2026-10-25T00:30:00Z"),
            tomorrowAt(now, madrid, LocalTime.of(2, 30)),
        )
    }

    @Test
    fun nextWeekAt_esHoySieteDiasYNoMananaMasSiete() {
        val now = Instant.parse("2026-11-04T10:00:00Z")

        assertEquals(Instant.parse("2026-11-11T08:00:00Z"), nextWeekAt(now, madrid, nine))
    }

    @Test
    fun nextWeekAt_usaLaFechaLocalAunqueUtcSeaOtroDia() {
        val now = Instant.parse("2026-11-05T02:00:00Z")

        assertEquals(Instant.parse("2026-11-11T15:00:00Z"), nextWeekAt(now, mexico, nine))
    }

    @Test
    fun nextWeekAt_conHoraInexistenteEnSaltoDeVerano_devuelveElInstantePosterior() {
        val now = Instant.parse("2026-03-22T10:00:00Z")

        assertEquals(
            Instant.parse("2026-03-29T01:30:00Z"),
            nextWeekAt(now, madrid, LocalTime.of(2, 30)),
        )
    }
}
