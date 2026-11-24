package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.reminder.SnoozeOption
import com.simposio.taskeer.domain.reminder.resolve
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SnoozeOptionTest {

    private val madrid: ZoneId = ZoneId.of("Europe/Madrid")
    private val now: Instant = Instant.parse("2026-11-04T10:00:00Z")
    private val nine: LocalTime = LocalTime.of(9, 0)

    @Test
    fun laterToday_sumaCincoHoras() {
        assertEquals(
            Instant.parse("2026-11-04T15:00:00Z"),
            SnoozeOption.LATER_TODAY.resolve(now, madrid, nine),
        )
    }

    @Test
    fun tomorrow_usaHoraPorDefectoYZona() {
        assertEquals(
            Instant.parse("2026-11-05T08:00:00Z"),
            SnoozeOption.TOMORROW.resolve(now, madrid, nine),
        )
        assertEquals(
            Instant.parse("2026-11-05T07:30:00Z"),
            SnoozeOption.TOMORROW.resolve(now, madrid, LocalTime.of(8, 30)),
        )
    }

    @Test
    fun nextWeek_sumaSieteDiasALaHoraPorDefecto() {
        assertEquals(
            Instant.parse("2026-11-11T08:00:00Z"),
            SnoozeOption.NEXT_WEEK.resolve(now, madrid, nine),
        )
    }
}
