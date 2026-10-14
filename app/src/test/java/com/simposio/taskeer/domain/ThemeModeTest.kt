package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.model.ThemeMode
import com.simposio.taskeer.domain.theme.resolveIsDark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun auto_sigueAlSistema() {
        assertTrue(ThemeMode.AUTO.resolveIsDark(systemDark = true))
        assertFalse(ThemeMode.AUTO.resolveIsDark(systemDark = false))
    }

    @Test
    fun light_fuerzaClaroAunqueElSistemaSeaOscuro() {
        assertFalse(ThemeMode.LIGHT.resolveIsDark(systemDark = true))
    }

    @Test
    fun dark_fuerzaOscuroAunqueElSistemaSeaClaro() {
        assertTrue(ThemeMode.DARK.resolveIsDark(systemDark = false))
    }

    @Test
    fun fromStorage_valorIlegible_esAuto() {
        assertEquals(ThemeMode.AUTO, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.AUTO, ThemeMode.fromStorage("morado"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage("DARK"))
    }
}
