package com.simposio.taskeer.ui.auth

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginScreenSourceTest {

    private val screen = MainSources.kotlin("ui/auth/LoginScreen.kt")

    @Test
    fun usaComponentesNativosDeMaterial3() {
        listOf("OutlinedTextField(", "OutlinedSecureTextField(", "Button(", "CircularProgressIndicator(")
            .forEach { assertTrue("falta $it", screen.contains(it)) }
    }

    @Test
    fun contraseñaUsaCampoSeguroConAlternarYContentDescription() {
        assertTrue(screen.contains("textObfuscationMode"))
        assertTrue(screen.contains("R.string.auth_password_show"))
        assertTrue(screen.contains("R.string.auth_password_hide"))
        assertTrue(screen.contains("ActionIcon("))
        assertTrue(screen.contains("ContentType.Password"))
        assertTrue(screen.contains("ContentType.Username"))
    }

    @Test
    fun noHayOlvidasteYSoloUnAccesoAlRegistro() {
        val strings = MainSources.strings().lines().filter { it.contains("name=\"auth_") }.joinToString("\n").lowercase()

        listOf("olvidaste", "registr").forEach {
            assertFalse("sobra $it", screen.lowercase().contains(it) || strings.contains(it))
        }
        assertEquals(1, Regex("R\\.string\\.register_link_action").findAll(screen).count())
    }

    @Test
    fun hayUnBotonContinuarConGoogle() {
        assertEquals(1, Regex("R\\.string\\.google_continue_action").findAll(screen).count())
        assertEquals(
            "Continuar con Google",
            Regex("name=\"google_continue_action\">(.*?)<").find(MainSources.strings())?.groupValues?.get(1),
        )
    }

    @Test
    fun elBotonDeGoogleEsUnOutlinedButtonNativo() {
        val section = screen.substringAfter("private fun GoogleSignInSection").substringBefore("private fun loginErrorText")

        assertTrue(section.contains("OutlinedButton("))
        assertTrue(section.contains("heightIn(min = Spacing.MinTouchTarget)"))
        assertTrue(section.contains("CircularProgressIndicator("))
        assertTrue(section.contains("stateDescription"))
        assertFalse(section.contains("painterResource") || section.contains("Image("))
    }

    @Test
    fun elBotonSeOmiteSiGoogleNoEstaDisponible() {
        assertTrue(screen.contains("if (state.googleAvailable) {\n            GoogleSignInSection(state, onGoogle)"))
        assertTrue(screen.contains("enabled = state.canUseGoogle"))
    }

    @Test
    fun lasContrasenasSigueSinRememberSaveable() {
        assertFalse(screen.contains("rememberSaveable"))
        assertFalse(MainSources.kotlin("ui/auth/GoogleCodesContent.kt").contains("rememberSaveable"))
    }

    @Test
    fun laActivitySoloSePasaComoHost() {
        assertTrue(screen.contains("LocalActivity.current"))
        assertTrue(screen.contains("activity?.let(viewModel::signInWithGoogle)"))
    }

    @Test
    fun noHayBotonDeGoogleEnElRegistro() {
        assertFalse(MainSources.kotlin("ui/register/RegisterScreen.kt").lowercase().contains("google"))
    }

    @Test
    fun muestraElAvisoDeCuentaCreadaConLiveRegion() {
        val notice = screen.indexOf("R.string.register_account_created_notice")

        assertTrue(notice >= 0)
        assertTrue(screen.substring(notice).contains("liveRegion = LiveRegionMode.Polite"))
    }

    @Test
    fun noSugiereLoginObligatorio_textoDeAyudaOpcional() {
        assertTrue(screen.contains("R.string.auth_login_optional_hint"))
        assertTrue(MainSources.strings().contains("Es opcional: tus datos siguen guardados en este dispositivo"))
    }

    @Test
    fun noConservaLaPasswordFueraDeLaPantalla() {
        assertTrue(screen.contains("remember { TextFieldState() }"))
        assertTrue(screen.contains("clearText()"))
        assertTrue(screen.contains("DisposableEffect"))
        assertFalse(screen.contains("rememberSaveable"))
        assertFalse(screen.contains("rememberTextFieldState"))
    }

    @Test
    fun paginaNoCompatibleUsaStatePage() {
        assertTrue(screen.contains("StatePage("))
        assertTrue(screen.contains("LoginBlock.NOT_SUPPORTED"))
        assertTrue(screen.contains("R.string.auth_not_supported_title"))
    }

    @Test
    fun erroresSeAnuncianConLiveRegion() {
        assertTrue(screen.contains("liveRegion = LiveRegionMode.Polite"))
    }

    @Test
    fun hayAccesoATengoUnEnlace() {
        assertEquals(1, Regex("R\\.string\\.verify_have_link").findAll(screen).count())
        assertTrue(screen.contains("onHaveVerificationLink"))
    }

    @Test
    fun sigueHabiendoUnSoloAccesoARegistro() {
        assertEquals(1, Regex("R\\.string\\.register_link_action").findAll(screen).count())
    }

    @Test
    fun hayAccesoARecuperarConUnCodigo() {
        assertEquals(1, Regex("R\\.string\\.reset_login_action").findAll(screen).count())
        assertTrue(screen.contains("onRecoverAccess"))
        assertEquals("Recuperar acceso con un código", Regex("name=\"reset_login_action\">(.*?)<").find(MainSources.strings())?.groupValues?.get(1))
    }

    @Test
    fun avisoDeReset_seAnunciaConLiveRegion() {
        val notice = screen.indexOf("R.string.reset_login_notice")

        assertTrue(notice >= 0)
        assertTrue(screen.contains("R.string.reset_login_notice_codes"))
        assertTrue(screen.lowercase().contains("liveregion"))
    }
}
