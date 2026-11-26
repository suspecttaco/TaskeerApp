package com.simposio.taskeer.ui.accountsecurity

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangePasswordScreenSourceTest {

    private val screen = MainSources.kotlin("ui/accountsecurity/ChangePasswordScreen.kt")

    @Test
    fun usaComponentesNativosDeMaterial3() {
        listOf("OutlinedSecureTextField(", "Button(", "CircularProgressIndicator(", "StatePage(")
            .forEach { assertTrue("falta $it", screen.contains(it)) }
    }

    @Test
    fun camposSeguros_conTextFieldStateYRemember() {
        assertEquals(3, Regex("remember \\{ TextFieldState\\(\\) \\}").findAll(screen).count())
        assertEquals(3, Regex("OutlinedSecureTextField\\(").findAll(screen).count())
        assertTrue(screen.contains("clearText()"))
        assertTrue(screen.contains("DisposableEffect"))
        assertTrue(screen.contains("R.string.auth_password_show"))
    }

    @Test
    fun noUsaRememberSaveableNiSavedStateHandle() {
        assertFalse(screen.contains("rememberSaveable"))
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
}
