package com.simposio.taskeer.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ContrastTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun negroSobreBlanco_es21() {
        assertEquals(21.0, contrastRatio(black, white), 0.001)
    }

    @Test
    fun mismoColor_es1() {
        assertEquals(1.0, contrastRatio(0xFF1565C0.toInt(), 0xFF1565C0.toInt()), 0.0001)
    }

    @Test
    fun simetrico() {
        assertEquals(contrastRatio(0xFF1565C0.toInt(), white), contrastRatio(white, 0xFF1565C0.toInt()), 0.0)
    }
}
