package com.simposio.taskeer.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionSpecsTest {

    @Test
    fun sinAnimaciones_losSpecsSonSnap() {
        assertTrue(phaseFadeSpec(false) is SnapSpec)
        assertTrue(stateFloatSpec(false) is SnapSpec)
        assertTrue(stateDpSpec(false) is SnapSpec)
    }

    @Test
    fun conAnimaciones_losSpecsSonTweenConSuDuracion() {
        assertEquals(Motion.PhaseFadeMillis, (phaseFadeSpec(true) as TweenSpec).durationMillis)
        assertEquals(Motion.StateChangeMillis, (stateFloatSpec(true) as TweenSpec).durationMillis)
        assertEquals(Motion.StateChangeMillis, (stateDpSpec(true) as TweenSpec).durationMillis)
    }

    @Test
    fun sinAnimaciones_lasTransicionesDeEstadoSonNone() {
        assertEquals(EnterTransition.None, stateFadeEnter(false))
        assertEquals(ExitTransition.None, stateFadeExit(false))
    }

    @Test
    fun conAnimaciones_lasTransicionesDeEstadoNoSonNone() {
        assertNotEquals(EnterTransition.None, stateFadeEnter(true))
        assertNotEquals(ExitTransition.None, stateFadeExit(true))
    }
}
