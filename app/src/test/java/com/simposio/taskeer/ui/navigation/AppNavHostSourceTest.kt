package com.simposio.taskeer.ui.navigation

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavHostSourceTest {

    private val source = MainSources.kotlin("ui/navigation/AppNavHost.kt")

    @Test
    fun noHayGuardDeSesion_yElInicioSigueSiendoMiDia() {
        assertTrue(source.contains("startDestination = AppDestination.START.route"))
        assertEquals(AppDestination.MY_DAY, AppDestination.START)
        assertFalse(source.contains("SessionState"))
        assertFalse(source.contains("sessionState"))
    }

    @Test
    fun loginSeRegistraComoDestino() {
        assertTrue(source.contains("composable(AppDestination.LOGIN.route)"))
        assertTrue(source.contains("LoginScreen("))
        assertTrue(source.contains("AppDestination.LOGIN.route"))
    }

    @Test
    fun hayRutaRegister() {
        assertTrue(source.contains("composable(AppDestination.REGISTER.route)"))
        assertTrue(source.contains("RegisterScreen("))
    }

    @Test
    fun registroVuelveAlLoginConElCorreoPrecargado() {
        assertTrue(source.contains("var pendingLogin by remember { mutableStateOf<LoginHandoff?>(null) }"))
        assertTrue(source.contains("LoginHandoff(it, LoginNotice.ACCOUNT_CREATED)"))
        assertFalse(source.contains("rememberSaveable"))
        assertTrue(source.contains("popUpTo(AppDestination.SETTINGS.route)"))
        assertTrue(source.contains("prefill = pendingLogin"))
    }

    @Test
    fun elCorreoPrecargadoSeConsumeUnaVez() {
        assertTrue(source.contains("onPrefillConsumed = { pendingLogin = null }"))
    }

    @Test
    fun sigueSinGuardDeSesion_yElInicioSigueSiendoMiDia() {
        assertTrue(source.contains("startDestination = AppDestination.START.route"))
        assertFalse(source.contains("SessionState"))
    }

    @Test
    fun hayRutaVerifyEmailSinArgumentos() {
        assertTrue(source.contains("composable(AppDestination.VERIFY_EMAIL.route)"))
        assertTrue(source.contains("VerifyEmailScreen("))
        assertFalse(AppDestination.VERIFY_EMAIL.route.contains("{"))
    }

    @Test
    fun losAccesosAVerificarCorreoVienenDeAjustesElLoginYLasInvitaciones() {
        assertEquals(3, Regex("navigate\\(AppDestination\\.VERIFY_EMAIL\\.route\\)").findAll(source).count())
    }

    @Test
    fun resetPassword_vuelveAlLoginConAviso() {
        assertTrue(source.contains("composable(AppDestination.RESET_PASSWORD.route)"))
        assertTrue(source.contains("ResetPasswordScreen("))
        assertTrue(source.contains("LoginHandoff(email, LoginNotice.PASSWORD_RESET)"))
        assertTrue(source.contains("navController.popBackStack(AppDestination.LOGIN.route, inclusive = false)"))
        assertTrue(source.contains("navigate(AppDestination.RESET_PASSWORD.route)"))
    }

    @Test
    fun hayRutasChangePasswordYRegenerateCodesSinArgumentos() {
        assertTrue(source.contains("composable(AppDestination.CHANGE_PASSWORD.route)"))
        assertTrue(source.contains("composable(AppDestination.REGENERATE_CODES.route)"))
        assertTrue(source.contains("ChangePasswordScreen("))
        assertTrue(source.contains("RegenerateCodesScreen("))
        assertFalse(AppDestination.CHANGE_PASSWORD.route.contains("{"))
        assertFalse(AppDestination.REGENERATE_CODES.route.contains("{"))
        assertTrue(source.contains("navigate(AppDestination.CHANGE_PASSWORD.route)"))
        assertTrue(source.contains("navigate(AppDestination.REGENERATE_CODES.route)"))
    }
}
