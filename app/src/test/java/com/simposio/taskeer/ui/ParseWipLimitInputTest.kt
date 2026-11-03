package com.simposio.taskeer.ui

import com.simposio.taskeer.ui.columns.WipLimitInput
import com.simposio.taskeer.ui.columns.parseWipLimitInput
import org.junit.Assert.assertEquals
import org.junit.Test

class ParseWipLimitInputTest {

    @Test
    fun parse_vacio_esEmpty() {
        assertEquals(WipLimitInput.Empty, parseWipLimitInput(""))
    }

    @Test
    fun parse_soloEspacios_esEmpty() {
        assertEquals(WipLimitInput.Empty, parseWipLimitInput("   "))
    }

    @Test
    fun parse_entero_esValue() {
        assertEquals(WipLimitInput.Value(3), parseWipLimitInput("3"))
    }

    @Test
    fun parse_enteroConEspacios_esValue() {
        assertEquals(WipLimitInput.Value(5), parseWipLimitInput(" 5 "))
    }

    @Test
    fun parse_noNumerico_esInvalid() {
        assertEquals(WipLimitInput.Invalid, parseWipLimitInput("abc"))
    }

    @Test
    fun parse_negativo_sePasaAlDominioComoValue() {
        assertEquals(WipLimitInput.Value(-1), parseWipLimitInput("-1"))
    }
}
