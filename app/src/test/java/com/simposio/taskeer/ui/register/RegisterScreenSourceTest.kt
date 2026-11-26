package com.simposio.taskeer.ui.register

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterScreenSourceTest {

    private val screen = MainSources.kotlin("ui/register/RegisterScreen.kt")
    private val panel = MainSources.kotlin("ui/register/RecoveryCodesPanel.kt")
    private val device = MainSources.kotlin("ui/register/RecoveryCodesDevice.kt")

    @Test
    fun usaComponentesNativosDeMaterial3() {
        val all = screen + panel
        listOf(
            "OutlinedTextField(",
            "OutlinedSecureTextField(",
            "Button(",
            "Checkbox(",
            "AlertDialog(",
            "CircularProgressIndicator(",
        ).forEach { assertTrue("falta $it", all.contains(it)) }
    }

    @Test
    fun camposDeContrasenaUsanAlternarYContentDescription() {
        assertTrue(screen.contains("textObfuscationMode"))
        assertTrue(screen.contains("R.string.auth_password_show"))
        assertTrue(screen.contains("R.string.auth_password_hide"))
        assertTrue(screen.contains("ActionIcon("))
        assertTrue(screen.contains("supportingText"))
    }

    @Test
    fun declaraContentTypesDeAutocompletado() {
        listOf("ContentType.PersonFullName", "ContentType.NewUsername", "ContentType.NewPassword")
            .forEach { assertTrue("falta $it", screen.contains(it)) }
        assertTrue(screen.contains("KeyboardCapitalization.Words"))
        assertTrue(screen.contains("ImeAction.Done"))
    }

    @Test
    fun noConservaLasPasswordsFueraDeLaPantalla() {
        assertTrue(screen.contains("remember { TextFieldState() }"))
        assertTrue(screen.contains("clearText()"))
        assertTrue(screen.contains("DisposableEffect"))
        assertFalse(screen.contains("rememberSaveable"))
        assertFalse(screen.contains("rememberTextFieldState"))
        assertFalse(screen.contains("SavedStateHandle"))
    }

    @Test
    fun erroresYResultadosSeAnuncianConLiveRegion() {
        assertTrue(screen.contains("liveRegion = LiveRegionMode.Polite"))
        assertTrue(panel.contains("liveRegion = LiveRegionMode.Polite"))
    }

    @Test
    fun codigosSeMuestranEnPanelSinEstado() {
        assertTrue(screen.contains("RecoveryCodesPanel("))
        assertTrue(panel.contains("codes: RecoveryCodes"))
        assertFalse(panel.contains("remember"))
        assertFalse(Regex("remember\\s*[({][^\\n]*(\\bcodes\\b|recoveryCodes)").containsMatchIn(screen))
    }

    @Test
    fun copiarMarcaElPortapapelesComoSensible() {
        assertTrue(device.contains("android.content.extra.IS_SENSITIVE"))
        assertTrue(device.contains("putBoolean(EXTRA_IS_SENSITIVE, true)"))
        assertTrue(device.contains("setClipEntry(ClipEntry(clip))"))
        assertTrue(screen.contains("copySensitive("))
    }

    @Test
    fun alSalirVaciaElPortapapelesSiSeCopio() {
        assertTrue(device.contains("setClipEntry(null)"))
        assertTrue(screen.contains("if (viewModel.uiState.value.copied)"))
        assertTrue(screen.contains("clipboard.clearCopied()"))
        assertTrue(screen.contains("onClick = { leave(viewModel::confirmCodesSaved) }") || screen.contains("leave(viewModel::confirmCodesSaved)"))
        assertTrue(screen.contains("leave(viewModel::leaveWithoutSaving)"))
    }

    @Test
    fun guardarUsaCreateDocumentSinPermisos() {
        assertTrue(device.contains("CreateDocument(\"text/plain\")"))
        assertTrue(device.contains("openOutputStream(uri, \"w\")"))
        listOf("WRITE_EXTERNAL_STORAGE", "MediaStore", "FileProvider").forEach {
            assertFalse("sobra $it", (screen + panel + device).contains(it))
        }
    }

    @Test
    fun guardarNoEscribeEnElMainThread() {
        assertTrue(device.contains("withContext(Dispatchers.IO)"))
    }

    @Test
    fun cancelarElSelectorNoMuestraError() {
        assertTrue(device.contains("if (uri != null && codes != null)"))
    }

    @Test
    fun codigosUsanFlagSecureSoloEnFaseCodes() {
        assertTrue(device.contains("WindowManager.LayoutParams.FLAG_SECURE"))
        assertTrue(device.contains("DisposableEffect(activity, enabled)"))
        assertTrue(device.contains("clearFlags(WindowManager.LayoutParams.FLAG_SECURE)"))
        val codesContent = screen.substringAfter("private fun CodesContent")
        assertTrue(codesContent.contains("SecureWindowEffect(enabled = true)"))
        assertFalse(screen.substringBefore("private fun CodesContent").contains("SecureWindowEffect("))
    }

    @Test
    fun atrasEnCodigosPideConfirmacion() {
        assertTrue(screen.contains("BackHandler(enabled = true) { showExit = true }"))
        assertTrue(screen.contains("LocalBackGuard.current"))
        assertTrue(screen.contains("backGuard.install { showExit = true }"))
        assertTrue(screen.contains("R.string.register_exit_stay"))
        assertTrue(screen.contains("R.string.register_exit_leave"))
    }

    @Test
    fun continuarSoloConCasilla() {
        assertTrue(panel.contains("enabled = canContinue"))
        assertTrue(screen.contains("canContinue = state.canContinue"))
        assertTrue(panel.contains("Role.Checkbox"))
    }

    @Test
    fun sinCodigosUsaStatePage() {
        assertTrue(screen.contains("RegisterPhase.NO_CODES ->"))
        assertTrue(screen.contains("R.string.register_no_codes_message"))
    }

    @Test
    fun codesLostUsaStatePage() {
        assertTrue(screen.contains("RegisterPhase.CODES_LOST ->"))
        assertTrue(screen.contains("R.string.register_codes_lost_message"))
    }

    @Test
    fun paginaNoCompatibleUsaStatePage() {
        assertTrue(screen.contains("RegisterBlock.NOT_SUPPORTED"))
        assertTrue(screen.contains("R.string.auth_not_supported_title"))
        assertTrue(screen.contains("StatePage("))
    }

    @Test
    fun alFinalizarLlamaAOnFinished() {
        assertTrue(screen.contains("if (state.phase == RegisterPhase.FINISHED) onFinished(state.finishedEmail)"))
    }

    @Test
    fun noHayBotonDeCompartirNiGoogle() {
        val all = (screen + panel + device).lowercase()

        listOf("action_send", "sharesheet", "intent.createchooser", "google").forEach {
            assertFalse("sobra $it", all.contains(it))
        }
    }
}
