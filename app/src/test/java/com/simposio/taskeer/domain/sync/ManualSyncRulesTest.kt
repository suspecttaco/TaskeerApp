package com.simposio.taskeer.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualSyncRulesTest {

    private val idle = SyncStatus.Idle(null)

    @Test
    fun completado_muestraTodoSincronizado() {
        assertEquals(ManualSyncFeedback.Status(idle), ManualSyncRules.result(CycleOutcome.COMPLETED, SyncStatus.Pending(2)))
    }

    @Test
    fun aplazadoConError_muestraElError() {
        val stuck = SyncStatus.Error(SyncErrorKind.STUCK_ENTRIES)

        assertEquals(ManualSyncFeedback.Status(stuck), ManualSyncRules.result(CycleOutcome.DEFERRED, stuck))
    }

    @Test
    fun aplazadoSinError_avisaDeReintento() {
        assertEquals(ManualSyncFeedback.Deferred, ManualSyncRules.result(CycleOutcome.DEFERRED, SyncStatus.Pending(1)))
        assertEquals(ManualSyncFeedback.Deferred, ManualSyncRules.result(null, idle))
    }

    @Test
    fun sinSesion_noAvisa() {
        assertNull(ManualSyncRules.result(CycleOutcome.NO_SESSION, idle))
    }

    @Test
    fun solicitud_soloConSesionSinCicloYSinPeticionPrevia() {
        assertTrue(ManualSyncRules.canRequest(idle, inFlight = false))
        assertFalse(ManualSyncRules.canRequest(SyncStatus.Syncing, inFlight = false))
        assertFalse(ManualSyncRules.canRequest(idle, inFlight = true))
        assertFalse(ManualSyncRules.canRequest(SyncStatus.Paused(PauseReason.NO_SESSION), inFlight = false))
    }
}
