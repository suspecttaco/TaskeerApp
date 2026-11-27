package com.simposio.taskeer.ui.theme

import com.simposio.taskeer.ui.components.CompletionMotion
import com.simposio.taskeer.ui.navigation.EditorMotion
import com.simposio.taskeer.ui.navigation.NavMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {

    @Test
    fun salida_esMasCortaQueLaEntrada() {
        assertTrue(Motion.ExitFadeMillis < Motion.EnterFadeMillis)
        assertTrue(Motion.PanelExitMillis < Motion.PanelEnterMillis)
        assertTrue(Motion.ValueFadeOutMillis < Motion.ValueFadeInMillis)
    }

    @Test
    fun duraciones_estanEnRangoRazonable() {
        listOf(
            Motion.ExitFadeMillis, Motion.EnterFadeMillis, Motion.ScaleMillis, Motion.PanelEnterMillis,
            Motion.PanelExitMillis, Motion.ValueFadeOutMillis, Motion.ValueFadeInMillis, Motion.PhaseFadeMillis,
            Motion.StateChangeMillis, Motion.PeekHalfMillis, Motion.BounceMillis, Motion.StrikeMillis,
        ).forEach { assertTrue("$it fuera de 1..1000 ms", it in 1..1000) }
    }

    @Test
    fun escalasYAlfas_estanEnRango() {
        assertTrue(Motion.ExitScale < 1f && Motion.EnterScale > 1f && Motion.BounceScale > 1f)
        assertTrue(Motion.DimAlpha in 0f..1f && Motion.DraggedSourceAlpha in 0f..1f)
    }

    @Test
    fun valoresPreviosDeNavegacionEditorYCompletar_noCambian() {
        assertEquals(90, NavMotion.ExitFadeMillis)
        assertEquals(210, NavMotion.EnterFadeMillis)
        assertEquals(300, NavMotion.ScaleMillis)
        assertEquals(0.92f, NavMotion.ExitScale)
        assertEquals(1.08f, NavMotion.EnterScale)
        assertEquals(180, NavMotion.PanelEnterMillis)
        assertEquals(120, NavMotion.PanelExitMillis)
        assertEquals(90, EditorMotion.ValueFadeOutMillis)
        assertEquals(150, EditorMotion.ValueFadeInMillis)
        assertEquals(500L, CompletionMotion.SettleMillis)
        assertEquals(1.25f, CompletionMotion.BounceScale)
        assertEquals(300, CompletionMotion.BounceMillis)
        assertEquals(300, CompletionMotion.StrikeMillis)
        assertEquals(0.6f, CompletionMotion.DimAlpha)
        assertEquals(300, Motion.PeekHalfMillis)
        assertEquals(0.3f, Motion.DraggedSourceAlpha)
    }
}
