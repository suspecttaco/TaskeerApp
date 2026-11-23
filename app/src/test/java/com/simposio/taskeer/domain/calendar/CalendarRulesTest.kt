package com.simposio.taskeer.domain.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarRulesTest {

    private val friday = LocalDate.of(2026, 11, 20)

    @Test
    fun periodFor_dia_esElAncla() {
        assertEquals(DateSpan(friday, friday.plusDays(1)), periodFor(CalendarMode.DAY, friday, DayOfWeek.MONDAY))
    }

    @Test
    fun periodFor_tresDias_esElAnclaYLosDosSiguientes() {
        val period = periodFor(CalendarMode.THREE_DAYS, friday, DayOfWeek.MONDAY)

        assertEquals(listOf(friday, friday.plusDays(1), friday.plusDays(2)), period.days)
    }

    @Test
    fun periodFor_semana_empiezaEnElPrimerDiaLunes() {
        val period = periodFor(CalendarMode.WEEK, friday, DayOfWeek.MONDAY)

        assertEquals(LocalDate.of(2026, 11, 16), period.start)
        assertEquals(LocalDate.of(2026, 11, 23), period.endExclusive)
    }

    @Test
    fun periodFor_semana_empiezaEnElPrimerDiaDomingo() {
        val period = periodFor(CalendarMode.WEEK, friday, DayOfWeek.SUNDAY)

        assertEquals(LocalDate.of(2026, 11, 15), period.start)
        assertEquals(7, period.days.size)
    }

    @Test
    fun periodFor_semana_siElAnclaYaEsElPrimerDiaNoRetrocede() {
        val monday = LocalDate.of(2026, 11, 16)

        assertEquals(monday, periodFor(CalendarMode.WEEK, monday, DayOfWeek.MONDAY).start)
    }

    @Test
    fun periodFor_agenda_esElMesNaturalDelAncla() {
        val period = periodFor(CalendarMode.AGENDA, friday, DayOfWeek.MONDAY)

        assertEquals(DateSpan(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1)), period)
        assertEquals(30, period.days.size)
    }

    @Test
    fun dateSpan_contiene_soloLosDiasDelRangoSemiabierto() {
        val span = DateSpan(friday, friday.plusDays(2))

        assertTrue(friday in span)
        assertTrue(friday.plusDays(1) in span)
        assertTrue(friday.plusDays(2) !in span)
        assertTrue(friday.minusDays(1) !in span)
    }

    @Test
    fun stepAnchor_avanzaYRetrocedeUnPeriodoPorModo() {
        assertEquals(friday.plusDays(1), stepAnchor(CalendarMode.DAY, friday, 1))
        assertEquals(friday.minusDays(1), stepAnchor(CalendarMode.DAY, friday, -1))
        assertEquals(friday.plusDays(3), stepAnchor(CalendarMode.THREE_DAYS, friday, 1))
        assertEquals(friday.minusDays(3), stepAnchor(CalendarMode.THREE_DAYS, friday, -1))
        assertEquals(friday.plusDays(7), stepAnchor(CalendarMode.WEEK, friday, 1))
        assertEquals(friday.minusDays(7), stepAnchor(CalendarMode.WEEK, friday, -1))
        assertEquals(LocalDate.of(2026, 12, 20), stepAnchor(CalendarMode.AGENDA, friday, 1))
        assertEquals(LocalDate.of(2026, 10, 20), stepAnchor(CalendarMode.AGENDA, friday, -1))
    }

    @Test
    fun stepAnchor_agenda_recortaElDiaAlFinDeMes() {
        assertEquals(LocalDate.of(2026, 2, 28), stepAnchor(CalendarMode.AGENDA, LocalDate.of(2026, 1, 31), 1))
        assertEquals(LocalDate.of(2028, 2, 29), stepAnchor(CalendarMode.AGENDA, LocalDate.of(2028, 1, 31), 1))
        assertEquals(LocalDate.of(2026, 2, 28), stepAnchor(CalendarMode.AGENDA, LocalDate.of(2026, 3, 31), -1))
    }

    @Test
    fun monthGrid_tieneSeisSemanasDeSieteDias() {
        val grid = monthGrid(friday, DayOfWeek.MONDAY)

        assertEquals(6, grid.size)
        assertTrue(grid.all { it.size == 7 })
        assertEquals(42, grid.flatten().distinct().size)
    }

    @Test
    fun monthGrid_lunes_empiezaEnElLunesQueContieneElDia1() {
        val grid = monthGrid(friday, DayOfWeek.MONDAY)

        assertEquals(LocalDate.of(2026, 10, 26), grid.first().first())
        assertEquals(LocalDate.of(2026, 11, 1), grid.first().last())
        assertEquals(DayOfWeek.MONDAY, grid[3].first().dayOfWeek)
    }

    @Test
    fun monthGrid_mesQueEmpiezaEnElPrimerDia_noAnadeSemanaPrevia() {
        val grid = monthGrid(friday, DayOfWeek.SUNDAY)

        assertEquals(LocalDate.of(2026, 11, 1), grid.first().first())
        assertEquals(LocalDate.of(2026, 12, 12), grid.last().last())
    }

    @Test
    fun monthGrid_mesDeVeintiochoDias_rellenaConElMesSiguiente() {
        val grid = monthGrid(LocalDate.of(2026, 2, 10), DayOfWeek.SUNDAY).flatten()

        assertEquals(LocalDate.of(2026, 2, 1), grid.first())
        assertEquals(28, grid.count { it.monthValue == 2 })
        assertEquals(LocalDate.of(2026, 3, 14), grid.last())
    }

    @Test
    fun weekdayHeader_siguePrimerDiaDeLaSemana() {
        assertEquals(
            listOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
            ),
            weekdayHeader(DayOfWeek.MONDAY),
        )
        assertEquals(DayOfWeek.SUNDAY, weekdayHeader(DayOfWeek.SUNDAY).first())
        assertEquals(DayOfWeek.SATURDAY, weekdayHeader(DayOfWeek.SUNDAY).last())
    }

    @Test
    fun loadSpanFor_cubreRejillaYPeriodoEnTodosLosModos() {
        CalendarMode.entries.forEach { mode ->
            listOf(friday, LocalDate.of(2026, 11, 30), LocalDate.of(2026, 2, 28)).forEach { anchor ->
                DayOfWeek.entries.forEach { first ->
                    val load = loadSpanFor(mode, anchor, first)
                    val grid = monthGrid(anchor, first).flatten()
                    val period = periodFor(mode, anchor, first)

                    assertTrue("rejilla $mode $anchor $first", grid.all { it in load })
                    assertTrue("periodo $mode $anchor $first", period.days.all { it in load })
                }
            }
        }
    }
}
