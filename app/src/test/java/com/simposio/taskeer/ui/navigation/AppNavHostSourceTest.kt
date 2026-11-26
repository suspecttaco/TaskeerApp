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
        assertTrue(source.contains("var pendingLoginEmail by remember { mutableStateOf<String?>(null) }"))
        assertFalse(source.contains("rememberSaveable"))
        assertTrue(source.contains("popUpTo(AppDestination.SETTINGS.route)"))
        assertTrue(source.contains("prefillEmail = pendingLoginEmail"))
    }

    @Test
    fun elCorreoPrecargadoSeConsumeUnaVez() {
        assertTrue(source.contains("onPrefillConsumed = { pendingLoginEmail = null }"))
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
    fun losAccesosAVerificarCorreoVienenDeAjustesYDelLogin() {
        assertEquals(2, Regex("navigate\\(AppDestination\\.VERIFY_EMAIL\\.route\\)").findAll(source).count())
    }
}
