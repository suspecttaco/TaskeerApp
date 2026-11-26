package com.simposio.taskeer.ui.passwordreset

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetPasswordScreenSourceTest {

    private val screen = MainSources.kotlin("ui/passwordreset/ResetPasswordScreen.kt")

    @Test
    fun usaComponentesNativosDeMaterial3() {
        listOf("OutlinedTextField(", "OutlinedSecureTextField(", "Button(", "CircularProgressIndicator(")
            .forEach { assertTrue("falta $it", screen.contains(it)) }
    }

    @Test
    fun camposSeguros_conTextFieldStateYRemember() {
        assertEquals4("remember { TextFieldState() }", screen)
        assertTrue(screen.contains("clearText()"))
        assertTrue(screen.contains("DisposableEffect"))
        assertTrue(screen.contains("textObfuscationMode"))
        assertTrue(screen.contains("R.string.auth_password_show"))
        assertTrue(screen.contains("R.string.auth_password_hide"))
        assertTrue(screen.contains("ContentType.NewPassword"))
    }

    @Test
    fun codigoSinAutocorreccionEnMayusculas() {
        assertTrue(screen.contains("KeyboardCapitalization.Characters"))
        assertTrue(screen.contains("KeyboardType.Ascii"))
        assertTrue(screen.contains("autoCorrectEnabled = false"))
    }

    @Test
    fun noUsaRememberSaveableNiSavedStateHandle() {
        assertFalse(screen.contains("rememberSaveable"))
        assertFalse(screen.contains("rememberTextFieldState"))
        assertFalse(screen.contains("SavedStateHandle"))
    }

    @Test
    fun erroresConLiveRegion() {
        assertTrue(screen.contains("liveRegion = LiveRegionMode.Polite"))
    }

    @Test
    fun botonDeshabilitadoMientrasEnvia() {
        assertTrue(screen.contains("enabled = state.canSubmit"))
        assertTrue(screen.contains("if (state.isSubmitting)"))
    }

    @Test
    fun versionNoCompatibleUsaStatePage() {
        assertTrue(screen.contains("ResetPasswordBlock.NOT_SUPPORTED"))
        assertTrue(screen.contains("R.string.auth_not_supported_title"))
    }

    @Test
    fun elCodigoSeVaciaTrasUn400() {
        assertTrue(screen.contains("state.incorrectCount"))
        assertTrue(screen.contains("codeState.clearText()"))
    }

    private fun assertEquals4(needle: String, haystack: String) {
        assertTrue(Regex(Regex.escape(needle)).findAll(haystack).count() >= 4)
    }
}
