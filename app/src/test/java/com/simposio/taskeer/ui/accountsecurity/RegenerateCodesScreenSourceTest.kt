package com.simposio.taskeer.ui.accountsecurity

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegenerateCodesScreenSourceTest {

    private val screen = MainSources.kotlin("ui/accountsecurity/RegenerateCodesScreen.kt")

    @Test
    fun usaComponentesNativosDeMaterial3() {
        listOf("OutlinedSecureTextField(", "Button(", "ButtonProgress(", "AlertDialog(")
            .forEach { assertTrue("falta $it", screen.contains(it)) }
    }

    @Test
    fun camposSeguros_conTextFieldStateYRemember() {
        assertTrue(screen.contains("remember { TextFieldState() }"))
        assertTrue(screen.contains("clearText()"))
        assertTrue(screen.contains("DisposableEffect"))
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
        assertTrue(screen.contains("loading = state.isSubmitting"))
    }

    @Test
    fun regenerarUsaRecoveryCodesPanelYFlagSecure() {
        assertTrue(screen.contains("RecoveryCodesPanel("))
        assertTrue(screen.contains("SecureWindowEffect(enabled = true)"))
        assertTrue(screen.contains("copySensitive("))
        assertTrue(screen.contains("clipboard.clearCopied()"))
        assertTrue(screen.contains("rememberSaveCodesToFile("))
    }

    @Test
    fun regenerarInterceptaLaSalidaConBackGuard() {
        assertTrue(screen.contains("BackHandler(enabled = true)"))
        assertTrue(screen.contains("backGuard.install"))
        assertTrue(screen.contains("backGuard.clear()"))
        assertTrue(screen.contains("R.string.register_exit_leave"))
    }

    @Test
    fun noCopiaLosCodigosAUnRememberNiAlEstado() {
        assertFalse(Regex("remember\\s*[({][^\\n]*(\\bcodes\\b|recoveryCodes)").containsMatchIn(screen))
    }
}
