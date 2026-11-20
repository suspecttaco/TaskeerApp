package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.task.MORNING_TIME
import com.simposio.taskeer.domain.task.QuickDateChoice
import com.simposio.taskeer.domain.task.QuickDateShortcut
import com.simposio.taskeer.domain.task.RescheduleTarget
import com.simposio.taskeer.domain.task.quickDateOf
import com.simposio.taskeer.domain.task.quickDueFor
import com.simposio.taskeer.domain.task.rescheduledDue
import com.simposio.taskeer.domain.task.toggled
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickDateRulesTest {

    private val today = LocalDate.of(2026, 11, 17)

    @Test
    fun quickDueFor_mananaPorLaManana_esManana0900() {
        val due = quickDueFor(QuickDateChoice.Shortcut(QuickDateShortcut.TOMORROW_MORNING), today)

        assertEquals(LocalDateTime.of(2026, 11, 18, 9, 0), due)
    }

    @Test
    fun quickDueFor_proximaSemana_esHoyMasSiete() {
        val due = quickDueFor(QuickDateChoice.Shortcut(QuickDateShortcut.NEXT_WEEK), today)

        assertEquals(LocalDateTime.of(2026, 11, 24, 9, 0), due)
    }

    @Test
    fun quickDueFor_proximaSemana_coincideConReprogramar() {
        val due = quickDueFor(QuickDateChoice.Shortcut(QuickDateShortcut.NEXT_WEEK), today)

        assertEquals(rescheduledDue(null, RescheduleTarget.NEXT_WEEK, today), due)
    }

    @Test
    fun quickDueFor_algunDia_esNulo() {
        assertNull(quickDueFor(QuickDateChoice.Shortcut(QuickDateShortcut.SOMEDAY), today))
    }

    @Test
    fun quickDueFor_elegida_conservaLaFechaConHoraPorDefecto() {
        val due = quickDueFor(QuickDateChoice.Picked(LocalDate.of(2026, 1, 5)), today)

        assertEquals(LocalDateTime.of(2026, 1, 5, 9, 0), due)
    }

    @Test
    fun quickDueFor_sinElegir_esNulo() {
        assertNull(quickDueFor(QuickDateChoice.None, today))
    }

    @Test
    fun quickDueFor_cruceDeMesYDeAnio() {
        val newYearsEve = LocalDate.of(2026, 12, 31)

        assertEquals(
            LocalDateTime.of(2027, 1, 1, 9, 0),
            quickDueFor(QuickDateChoice.Shortcut(QuickDateShortcut.TOMORROW_MORNING), newYearsEve),
        )
        assertEquals(
            LocalDateTime.of(2027, 1, 7, 9, 0),
            quickDueFor(QuickDateChoice.Shortcut(QuickDateShortcut.NEXT_WEEK), newYearsEve),
        )
    }

    @Test
    fun morningTime_es0900() {
        assertEquals(LocalTime.of(9, 0), MORNING_TIME)
    }

    @Test
    fun quickDateOf_algunDiaYNone_sonNulos() {
        assertNull(quickDateOf(QuickDateChoice.None, today))
        assertNull(quickDateOf(QuickDateChoice.Shortcut(QuickDateShortcut.SOMEDAY), today))
        assertEquals(
            LocalDate.of(2026, 11, 18),
            quickDateOf(QuickDateChoice.Shortcut(QuickDateShortcut.TOMORROW_MORNING), today),
        )
    }

    @Test
    fun toggled_mismoAtajoDeselecciona() {
        val choice = QuickDateChoice.Shortcut(QuickDateShortcut.NEXT_WEEK)

        assertEquals(QuickDateChoice.None, toggled(choice, QuickDateShortcut.NEXT_WEEK))
    }

    @Test
    fun toggled_otroAtajoSustituyeIncluidaUnaFechaElegida() {
        val picked = QuickDateChoice.Picked(LocalDate.of(2026, 12, 1))

        assertEquals(
            QuickDateChoice.Shortcut(QuickDateShortcut.SOMEDAY),
            toggled(picked, QuickDateShortcut.SOMEDAY),
        )
    }
}
