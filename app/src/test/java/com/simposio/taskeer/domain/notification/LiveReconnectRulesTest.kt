package com.simposio.taskeer.domain.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveReconnectRulesTest {

    @Test
    fun primerFallo_esLaBase() {
        assertEquals(2_000L, LiveReconnectRules.delayMillis(1, null, 0.0))
    }

    @Test
    fun duplicaHastaElTope() {
        val delays = (1..7).map { LiveReconnectRules.delayMillis(it, null, 0.0) }

        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L), delays)
    }

    @Test
    fun nuncaSuperaElTopeMasJitter() {
        val worst = listOf(1, 5, 50, 1_000, Int.MAX_VALUE).maxOf { LiveReconnectRules.delayMillis(it, null, 1.0) }

        assertEquals(72_000L, worst)
    }

    @Test
    fun jitterAcotadoAl20Porciento() {
        assertEquals(8_000L * 0.8, LiveReconnectRules.delayMillis(3, null, -1.0).toDouble(), 0.5)
        assertEquals(8_000L * 1.2, LiveReconnectRules.delayMillis(3, null, 1.0).toDouble(), 0.5)
        assertEquals(LiveReconnectRules.delayMillis(3, null, 1.0), LiveReconnectRules.delayMillis(3, null, 9.0))
    }

    @Test
    fun retryAfterEsSuelo() {
        assertEquals(30_000L, LiveReconnectRules.delayMillis(1, 30L, 0.0))
        assertEquals(2_000L, LiveReconnectRules.delayMillis(1, 1L, 0.0))
    }

    @Test
    fun retryAfterSeTopaA15Minutos() {
        assertEquals(900_000L, LiveReconnectRules.delayMillis(1, 86_400L, 0.0))
    }

    @Test
    fun reiniciaTrasConexionEstable() {
        assertTrue(LiveReconnectRules.shouldResetFailures(10_000L, false))
        assertTrue(LiveReconnectRules.shouldResetFailures(0L, true))
    }

    @Test
    fun noReiniciaSiCierraAntesDe10sSinEventos() {
        assertFalse(LiveReconnectRules.shouldResetFailures(9_999L, false))
    }
}
