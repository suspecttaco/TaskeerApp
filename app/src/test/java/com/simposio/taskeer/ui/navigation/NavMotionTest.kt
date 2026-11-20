package com.simposio.taskeer.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.SnapSpec
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

    @Test
    fun editorMotion_duracionesPositivasYAcotadas() {
        val millis = listOf(
            EditorMotion.PanelFadeOutMillis,
            EditorMotion.PanelFadeInMillis,
            EditorMotion.ValueFadeOutMillis,
            EditorMotion.ValueFadeInMillis,
            EditorMotion.SizeMillis,
            EditorMotion.RowEnterMillis,
            EditorMotion.RowExitMillis,
        )

        assertTrue(millis.all { it in 1..400 })
        assertTrue(EditorMotion.PanelScale < 1f)
    }

    @Test
    fun editorPanelTransition_conEscalaCero_esInstantanea() {
        val transition = editorPanelTransition(animated = false)

        assertEquals(EnterTransition.None, transition.targetContentEnter)
        assertEquals(ExitTransition.None, transition.initialContentExit)
        assertEquals(EnterTransition.None, editorValueTransition(animated = false).targetContentEnter)
        assertEquals(ExitTransition.None, editorValueTransition(animated = false).initialContentExit)
    }

    @Test
    fun editorPanelTransition_conEscala_noEsNinguna() {
        val transition = editorPanelTransition(animated = true)

        assertNotEquals(EnterTransition.None, transition.targetContentEnter)
        assertNotEquals(ExitTransition.None, transition.initialContentExit)
        assertNotEquals(EnterTransition.None, editorValueTransition(animated = true).targetContentEnter)
    }

    @Test
    fun editorSizeSpec_conEscalaCero_esInstantaneo() {
        assertTrue(editorSizeSpec(animated = false) is SnapSpec)
        assertTrue(editorSizeSpec(animated = true) !is SnapSpec)
    }

    @Test
    fun editorRowEnter_conEscalaCero_esNone() {
        assertEquals(EnterTransition.None, editorRowEnter(animated = false))
        assertNotEquals(EnterTransition.None, editorRowEnter(animated = true))
    }

    @Test
    fun editorRowExit_conEscalaCero_esNone() {
        assertEquals(ExitTransition.None, editorRowExit(animated = false))
        assertNotEquals(ExitTransition.None, editorRowExit(animated = true))
    }
}
