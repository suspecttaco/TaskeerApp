package com.simposio.taskeer.ui

import com.simposio.taskeer.ui.components.CompletionMotion
import com.simposio.taskeer.ui.components.animationsEnabled
import com.simposio.taskeer.ui.components.settleMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionMotionTest {

    @Test
    fun constantes_estanEnSusRangos() {
        assertTrue(CompletionMotion.BounceScale > 1f)
        assertTrue(CompletionMotion.DimAlpha > 0f && CompletionMotion.DimAlpha < 1f)
        assertTrue(CompletionMotion.BounceMillis > 0)
        assertTrue(CompletionMotion.StrikeMillis > 0)
        assertTrue(CompletionMotion.SettleMillis > 0L)
    }

    @Test
    fun animationsEnabled_conEscalaCero_esFalso() {
        assertFalse(animationsEnabled(0f))
        assertTrue(animationsEnabled(1f))
        assertTrue(animationsEnabled(0.5f))
    }

    @Test
    fun settleMillis_conEscalaCero_esCero() {
        assertEquals(0L, settleMillis(0f))
    }

    @Test
    fun settleMillis_conEscalaNormal_esLaConstante() {
        assertEquals(CompletionMotion.SettleMillis, settleMillis(1f))
        assertEquals(500L, settleMillis(1f))
    }
}
