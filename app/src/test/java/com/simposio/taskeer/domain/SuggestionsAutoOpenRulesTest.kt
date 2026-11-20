package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.myday.isFirstForegroundOfDay
import com.simposio.taskeer.domain.myday.shouldAutoOpenSuggestions
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsAutoOpenRulesTest {

    private val today = LocalDate.of(2026, 11, 19)

    @Test
    fun esPrimerPrimerPlano_sinHistorialOAnterior() {
        assertTrue(isFirstForegroundOfDay(today, null))
        assertTrue(isFirstForegroundOfDay(today, today.minusDays(1)))
    }

    @Test
    fun noEsPrimerPrimerPlano_mismoDia() {
        assertFalse(isFirstForegroundOfDay(today, today))
    }

    @Test
    fun noEsPrimerPrimerPlano_relojRetrocede() {
        assertFalse(isFirstForegroundOfDay(today, today.plusDays(1)))
    }

    @Test
    fun abre_enMiDiaConSugerenciasYSinBloqueo() {
        assertTrue(shouldAutoOpenSuggestions(onMyDay = true, count = 1, blocked = false))
    }

    @Test
    fun noAbre_sinSugerencias() {
        assertFalse(shouldAutoOpenSuggestions(onMyDay = true, count = 0, blocked = false))
    }

    @Test
    fun noAbre_fueraDeMiDia() {
        assertFalse(shouldAutoOpenSuggestions(onMyDay = false, count = 5, blocked = false))
    }

    @Test
    fun noAbre_conVisorDeAvisosOHojaAbierta() {
        assertFalse(shouldAutoOpenSuggestions(onMyDay = true, count = 5, blocked = true))
    }
}
