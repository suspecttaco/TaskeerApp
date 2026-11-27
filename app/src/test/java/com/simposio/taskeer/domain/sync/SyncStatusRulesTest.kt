package com.simposio.taskeer.domain.sync

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStatusRulesTest {

    private val last = Instant.parse("2026-11-26T18:30:00Z")

    @Test
    fun sinPendientes_idleConUltimaSincronizacion() {
        val status = SyncStatusRules.resolve(SyncSnapshot(signedIn = true, lastSyncedAt = last))

        assertEquals(SyncStatus.Idle(last), status)
    }

    @Test
    fun conPendientes_muestraElConteo() {
        assertEquals(SyncStatus.Pending(3), SyncStatusRules.resolve(SyncSnapshot(signedIn = true, pendingCount = 3)))
    }

    @Test
    fun cicloEnCurso_syncing() {
        assertEquals(SyncStatus.Syncing, SyncStatusRules.resolve(SyncSnapshot(signedIn = true, syncing = true, pendingCount = 2)))
    }

    @Test
    fun entradasAtascadas_error() {
        val status = SyncStatusRules.resolve(SyncSnapshot(signedIn = true, stuckCount = 1, pendingCount = 1))

        assertEquals(SyncStatus.Error(SyncErrorKind.STUCK_ENTRIES), status)
    }

    @Test
    fun pausada_ganaAlResto() {
        val status = SyncStatusRules.resolve(
            SyncSnapshot(signedIn = true, paused = PauseReason.ACCOUNT_MISMATCH, syncing = true, pendingCount = 4),
        )

        assertEquals(SyncStatus.Paused(PauseReason.ACCOUNT_MISMATCH), status)
    }

    @Test
    fun sinSesion_pausadaYOculta() {
        val status = SyncStatusRules.resolve(SyncSnapshot(signedIn = false, pendingCount = 4))

        assertEquals(SyncStatus.Paused(PauseReason.NO_SESSION), status)
        assertFalse(SyncStatusRules.isVisible(status))
    }

    @Test
    fun conSesion_todosLosEstadosSonVisibles() {
        assertTrue(SyncStatusRules.isVisible(SyncStatus.Idle(null)))
        assertTrue(SyncStatusRules.isVisible(SyncStatus.Paused(PauseReason.UPDATE_REQUIRED)))
    }
}
