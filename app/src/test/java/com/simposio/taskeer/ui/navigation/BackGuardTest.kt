package com.simposio.taskeer.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackGuardTest {

    @Test
    fun sinManejador_noIntercepta() {
        assertFalse(BackGuard().intercept())
    }

    @Test
    fun conManejador_interceptaYLoInvoca() {
        val guard = BackGuard()
        var calls = 0
        guard.install { calls++ }

        assertTrue(guard.intercept())
        assertEquals(1, calls)
    }

    @Test
    fun tras_clear_noIntercepta() {
        val guard = BackGuard()
        var calls = 0
        guard.install { calls++ }
        guard.clear()

        assertFalse(guard.intercept())
        assertEquals(0, calls)
    }

    @Test
    fun install_reemplazaAlManejadorAnterior() {
        val guard = BackGuard()
        val calls = mutableListOf<String>()
        guard.install { calls += "primero" }
        guard.install { calls += "segundo" }
        guard.intercept()

        assertEquals(listOf("segundo"), calls)
    }

    @Test
    fun interceptaVariasVeces_invocaCadaVez() {
        val guard = BackGuard()
        var calls = 0
        guard.install { calls++ }
        guard.intercept()
        guard.intercept()

        assertEquals(2, calls)
    }
}
