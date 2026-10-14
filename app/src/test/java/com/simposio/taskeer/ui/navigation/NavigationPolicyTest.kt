package com.simposio.taskeer.ui.navigation

import org.junit.Assert.assertEquals
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
}
