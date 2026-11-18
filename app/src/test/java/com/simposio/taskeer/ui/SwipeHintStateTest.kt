package com.simposio.taskeer.ui

import androidx.compose.runtime.saveable.SaverScope
import com.simposio.taskeer.ui.tasks.SwipeHintState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeHintStateTest {

    @Test
    fun primeraVez_reproduce() {
        assertTrue(SwipeHintState().shouldPlay())
    }

    @Test
    fun despuesDeMarcada_noReproduce() {
        val hint = SwipeHintState()

        hint.markPlayed()

        assertFalse(hint.shouldPlay())
    }

    @Test
    fun alRestaurar_conservaSiYaSeReprodujo() {
        val played = SwipeHintState().apply { markPlayed() }
        val saved = with(SwipeHintState.Saver) { SaverScope { true }.save(played) }

        assertEquals(true, saved)
        assertFalse(SwipeHintState.Saver.restore(requireNotNull(saved))!!.shouldPlay())
        assertTrue(SwipeHintState.Saver.restore(false)!!.shouldPlay())
    }
}
