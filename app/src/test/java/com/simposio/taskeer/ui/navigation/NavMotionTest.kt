package com.simposio.taskeer.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavMotionTest {
    @Test
    fun enter_sinAnimaciones_esNone() {
        assertEquals(EnterTransition.None, zoomOutEnter(animationsEnabled = false))
    }

    @Test
    fun exit_sinAnimaciones_esNone() {
        assertEquals(ExitTransition.None, zoomOutExit(animationsEnabled = false))
    }

    @Test
    fun enter_conAnimaciones_noEsNone() {
        assertNotEquals(EnterTransition.None, zoomOutEnter(animationsEnabled = true))
        assertNotEquals(ExitTransition.None, zoomOutExit(animationsEnabled = true))
    }

    @Test
    fun exitScale_esMenorQueUno() {
        assertTrue(NavMotion.ExitScale < 1f)
    }

    @Test
    fun enterScale_esMayorQueUno() {
        assertTrue(NavMotion.EnterScale > 1f)
    }

    @Test
    fun exitFade_esMasCortoQueElFundidoDeEntrada() {
        assertTrue(NavMotion.ExitFadeMillis < NavMotion.EnterFadeMillis)
    }

    @Test
    fun panel_sinAnimaciones_esNone() {
        assertEquals(EnterTransition.None, panelEnter(animationsEnabled = false))
        assertEquals(ExitTransition.None, panelExit(animationsEnabled = false))
    }

    @Test
    fun panel_conAnimaciones_noEsNone() {
        assertNotEquals(EnterTransition.None, panelEnter(animationsEnabled = true))
        assertNotEquals(ExitTransition.None, panelExit(animationsEnabled = true))
    }

    @Test
    fun panelExit_esMasCortoQueLaEntrada() {
        assertTrue(NavMotion.PanelExitMillis < NavMotion.PanelEnterMillis)
    }
}
