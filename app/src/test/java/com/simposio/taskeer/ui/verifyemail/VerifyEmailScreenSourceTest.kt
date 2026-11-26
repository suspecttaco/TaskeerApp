package com.simposio.taskeer.ui.verifyemail

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifyEmailScreenSourceTest {

    private val screen = MainSources.kotlin("ui/verifyemail/VerifyEmailScreen.kt")

    @Test
    fun usaComponentesNativosDeMaterial3() {
        listOf("OutlinedTextField(", "Button(", "CircularProgressIndicator(", "TextFieldLineLimits.MultiLine")
            .forEach { assertTrue("falta $it", screen.contains(it)) }
        assertTrue(screen.contains("ImeAction.Done"))
        assertTrue(screen.contains("autoCorrectEnabled = false"))
    }

    @Test
    fun noConservaElTextoFueraDeLaPantalla() {
        assertTrue(screen.contains("remember { TextFieldState() }"))
        assertTrue(screen.contains("clearText()"))
        assertFalse(screen.contains("rememberSaveable"))
        assertFalse(screen.contains("rememberTextFieldState"))
        assertFalse(screen.contains("SavedStateHandle"))
    }

    @Test
    fun erroresYResultadosConLiveRegion() {
        assertTrue(screen.contains("liveRegion = LiveRegionMode.Polite"))
    }

    @Test
    fun exitoUsaStatePage() {
        assertTrue(screen.contains("StatePage("))
        assertTrue(screen.contains("R.string.verify_done_title"))
        assertTrue(screen.contains("R.string.verify_done_action"))
    }

    @Test
    fun botonDeshabilitadoMientrasEnvia() {
        assertTrue(screen.contains("enabled = state.canSubmit"))
        assertTrue(screen.contains("if (state.isSubmitting)"))
    }
}
