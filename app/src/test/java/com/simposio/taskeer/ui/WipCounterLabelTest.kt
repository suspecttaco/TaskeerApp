package com.simposio.taskeer.ui

import com.simposio.taskeer.ui.board.wipCounterLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WipCounterLabelTest {

    @Test
    fun label_sinLimite_esNulo() {
        assertNull(wipCounterLabel(2, null))
    }

    @Test
    fun label_conLimite_muestraConteoYLimite() {
        assertEquals("2/3", wipCounterLabel(2, 3))
    }

    @Test
    fun label_sobrepasado_muestraConteoReal() {
        assertEquals("4/3", wipCounterLabel(4, 3))
    }
}
