package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.theme.ColorOrigin
import com.simposio.taskeer.domain.theme.isDynamicColorAvailable
import com.simposio.taskeer.domain.theme.resolveColorOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorOriginTest {

    @Test
    fun preferenciaOn_api31_usaDinamico() {
        assertEquals(ColorOrigin.DYNAMIC, resolveColorOrigin(dynamicColorEnabled = true, sdkInt = 31))
    }

    @Test
    fun preferenciaOn_api24_usaMarca() {
        assertEquals(ColorOrigin.BRAND, resolveColorOrigin(dynamicColorEnabled = true, sdkInt = 24))
    }

    @Test
    fun preferenciaOff_api31_usaMarca() {
        assertEquals(ColorOrigin.BRAND, resolveColorOrigin(dynamicColorEnabled = false, sdkInt = 31))
    }

    @Test
    fun preferenciaOff_api24_usaMarca() {
        assertEquals(ColorOrigin.BRAND, resolveColorOrigin(dynamicColorEnabled = false, sdkInt = 24))
    }

    @Test
    fun disponibilidadPorApi() {
        assertFalse(isDynamicColorAvailable(24))
        assertFalse(isDynamicColorAvailable(30))
        assertTrue(isDynamicColorAvailable(31))
        assertTrue(isDynamicColorAvailable(37))
    }
}
