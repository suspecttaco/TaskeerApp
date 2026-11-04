package com.simposio.taskeer.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPolicyTest {

    private val spec = topLevelNavSpec()

    @Test
    fun preservaYRestauraEstado() {
        assertTrue(spec.saveState)
        assertTrue(spec.restoreState)
    }

    @Test
    fun recortaHastaInicio() {
        assertEquals(AppDestination.START.route, spec.popUpToRoute)
        assertEquals("home", spec.popUpToRoute)
    }

    @Test
    fun unaSolaEntradaEnLaPila() {
        assertTrue(spec.launchSingleTop)
    }

    @Test
    fun volverDesdeAvisos_conAnterior_retrocede() {
        assertEquals(BackStep.Pop, backStep(AppDestination.NOTIFICATIONS, null, hasPrevious = true))
    }

    @Test
    fun volverDesdeAvisos_sinAnterior_navegaAInicio() {
        assertEquals(
            BackStep.Navigate("home"),
            backStep(AppDestination.NOTIFICATIONS, null, hasPrevious = false),
        )
    }

    @Test
    fun volver_enColumnas_conservaSuComportamiento() {
        assertEquals(BackStep.Pop, backStep(AppDestination.BOARD_COLUMNS, "b1", hasPrevious = true))
        assertEquals(
            BackStep.Navigate("board/b1"),
            backStep(AppDestination.BOARD_COLUMNS, "b1", hasPrevious = false),
        )
    }

    @Test
    fun volver_enDestinosSinBotonVolver_noHaceNada() {
        assertEquals(BackStep.None, backStep(AppDestination.HOME, null, hasPrevious = true))
        assertEquals(BackStep.None, backStep(AppDestination.BOARD, "b1", hasPrevious = true))
    }

    @Test
    fun shouldOpenViewer_conExtra_true() {
        assertTrue(shouldOpenViewer(hasExtra = true, isRestored = false, launchedFromHistory = false))
    }

    @Test
    fun shouldOpenViewer_sinExtra_false() {
        assertFalse(shouldOpenViewer(hasExtra = false, isRestored = false, launchedFromHistory = false))
    }

    @Test
    fun shouldOpenViewer_restaurada_false() {
        assertFalse(shouldOpenViewer(hasExtra = true, isRestored = true, launchedFromHistory = false))
    }

    @Test
    fun shouldOpenViewer_desdeHistorial_false() {
        assertFalse(shouldOpenViewer(hasExtra = true, isRestored = false, launchedFromHistory = true))
    }
}
