package com.simposio.taskeer.domain.sync

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundSyncRulesTest {

    private val now = Instant.parse("2026-11-27T12:00:00Z")

    @Test
    fun decide_todoResultadoDelMotorEsExito() {
        CycleOutcome.entries.forEach { outcome ->
            assertEquals("$outcome", BackgroundDecision.SUCCESS, BackgroundSyncRules.decide(outcome))
        }
    }

    @Test
    fun sessionNotReady_reintentaHastaTresVecesYLuegoExito() {
        val decisions = (0..3).map { BackgroundSyncRules.sessionNotReady(it) }

        assertEquals(
            listOf(BackgroundDecision.RETRY, BackgroundDecision.RETRY, BackgroundDecision.RETRY, BackgroundDecision.SUCCESS),
            decisions,
        )
    }

    @Test
    fun shouldRun_envioYReintentoSoloConPendientes() {
        listOf(BackgroundKind.PUSH, BackgroundKind.RETRY).forEach { kind ->
            assertFalse(BackgroundSyncRules.shouldRun(kind, 0, null, now))
            assertTrue(BackgroundSyncRules.shouldRun(kind, 1, now, now))
        }
    }

    @Test
    fun shouldRun_traidaSinSincronizacionPreviaCorre() {
        assertTrue(BackgroundSyncRules.shouldRun(BackgroundKind.PULL, 0, null, now))
    }

    @Test
    fun shouldRun_traidaOmiteDentroDeLaVentanaDeFrescura() {
        assertFalse(BackgroundSyncRules.shouldRun(BackgroundKind.PULL, 0, now - Duration.ofMinutes(4), now))
    }

    @Test
    fun shouldRun_traidaCorreEnLosCincoMinutosExactos() {
        assertTrue(BackgroundSyncRules.shouldRun(BackgroundKind.PULL, 0, now - Duration.ofMinutes(5), now))
    }

    @Test
    fun shouldRun_traidaOmiteUnSegundoAntesDelLimite() {
        assertFalse(BackgroundSyncRules.shouldRun(BackgroundKind.PULL, 0, now - Duration.ofSeconds(299), now))
    }

    @Test
    fun constantes_seisHorasCincoMinutosYTreintaSegundos() {
        assertEquals(Duration.ofHours(6), BackgroundSyncRules.PULL_INTERVAL)
        assertEquals(Duration.ofMinutes(5), BackgroundSyncRules.FRESH_WINDOW)
        assertEquals(Duration.ofSeconds(30), BackgroundSyncRules.SESSION_WAIT)
    }
}
