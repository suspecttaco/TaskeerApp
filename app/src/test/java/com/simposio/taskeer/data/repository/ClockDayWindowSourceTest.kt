package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.date.DayWindow
import com.simposio.taskeer.domain.date.TimeZoneProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClockDayWindowSourceTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun emiteLaVentanaActualAlSuscribirse() = runTest {
        val source = source(Instant.parse("2026-11-17T12:00:00Z")) { utc }

        val window = source.observe().first()

        assertEquals(LocalDate.of(2026, 11, 17), window.date)
        assertEquals(Instant.parse("2026-11-17T00:00:00Z"), window.start)
    }

    @Test
    fun alCruzarLaMedianoche_emiteLaVentanaDelDiaSiguiente() = runTest {
        val source = source(Instant.parse("2026-11-17T23:59:30Z")) { utc }
        val seen = collect(source)

        advanceTimeBy(31_000)

        assertEquals(listOf(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 18)), seen.map { it.date })
    }

    @Test
    fun alCambiarLaZona_emiteLaNuevaVentanaEnUnMinuto() = runTest {
        var zone = utc
        val source = source(Instant.parse("2026-11-17T12:00:00Z")) { zone }
        val seen = collect(source)
        assertEquals(1, seen.size)

        zone = ZoneId.of("Pacific/Auckland")
        advanceTimeBy(60_001)

        assertEquals(listOf(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 18)), seen.map { it.date })
    }

    @Test
    fun sinCambios_noReemiteLaMismaVentana() = runTest {
        val source = source(Instant.parse("2026-11-17T08:00:00Z")) { utc }
        val seen = collect(source)

        advanceTimeBy(10 * 60_000)

        assertEquals(1, seen.size)
    }

    @Test
    fun cambioDeZona_emiteVentanaNuevaSinEsperar() = runTest {
        var zone = utc
        val changes = MutableSharedFlow<Unit>()
        val source = ClockDayWindowSource(
            clock = { Instant.parse("2026-11-17T12:00:00Z").plusMillis(testScheduler.currentTime) },
            timeZone = { zone },
            zoneChanges = changes,
        )
        val seen = collect(source)
        assertEquals(listOf(LocalDate.of(2026, 11, 17)), seen.map { it.date })

        zone = ZoneId.of("Pacific/Auckland")
        changes.emit(Unit)

        assertEquals(listOf(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 18)), seen.map { it.date })
        assertEquals(0L, testScheduler.currentTime)
    }

    private fun TestScope.source(base: Instant, zone: TimeZoneProvider): ClockDayWindowSource =
        ClockDayWindowSource(clock = { base.plusMillis(testScheduler.currentTime) }, timeZone = zone)

    private fun TestScope.collect(source: ClockDayWindowSource): List<DayWindow> {
        val seen = mutableListOf<DayWindow>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observe().toList(seen) }
        return seen
    }
}
