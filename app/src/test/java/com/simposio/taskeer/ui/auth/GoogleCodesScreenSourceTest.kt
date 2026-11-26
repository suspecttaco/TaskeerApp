package com.simposio.taskeer.ui.auth

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleCodesScreenSourceTest {

    private val codes = MainSources.kotlin("ui/auth/GoogleCodesContent.kt")
    private val screen = MainSources.kotlin("ui/auth/LoginScreen.kt")

    @Test
    fun usaElPanelDeCodigosCompartido() {
        assertEquals(1, Regex("RecoveryCodesPanel\\(").findAll(codes).count())
    }

    @Test
    fun protegeLaVentanaYLaSalida() {
        assertTrue(codes.contains("SecureWindowEffect(enabled = true)"))
        assertTrue(codes.contains("BackHandler(enabled = true)"))
        assertTrue(codes.contains("backGuard.install"))
    }

    @Test
    fun copiaComoSensibleYVaciaElPortapapeles() {
        assertTrue(codes.contains("copySensitive("))
        assertTrue(codes.contains("clearCopied()"))
    }

    @Test
    fun noPersisteLosCodigos() {
        assertFalse(codes.contains("rememberSaveable"))
        assertFalse(codes.contains("SavedStateHandle"))
        assertFalse(codes.contains("Bundle"))
    }

    @Test
    fun elLoginMuestraLosCodigosEnLugarDelFormularioMientrasDure() {
        assertTrue(screen.contains("LoginPhase.CODES -> {\n            GoogleCodesContent(viewModel, state, modifier)"))
        assertTrue(screen.contains("LoginPhase.CODES_LOST"))
    }
}
