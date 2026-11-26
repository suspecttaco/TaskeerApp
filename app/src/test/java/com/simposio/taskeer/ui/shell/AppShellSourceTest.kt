package com.simposio.taskeer.ui.shell

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShellSourceTest {

    private val source = MainSources.kotlin("ui/shell/AppShell.kt")

    @Test
    fun avisaLaExpiracionConSnackbarYConfirmaElAviso() {
        assertTrue(source.contains("R.string.auth_session_expired"))
        assertTrue(source.contains("actionLabel = signInLabel"))
        assertTrue(source.contains("session.acknowledgeExpiration()"))
        assertTrue(source.contains("navController.navigate(AppDestination.LOGIN.route)"))
    }

    @Test
    fun avisaElResultadoDelCierreDeSesion() {
        assertTrue(source.contains("SessionMessage.LOGGED_OUT -> loggedOutText"))
        assertTrue(source.contains("SessionMessage.LOGGED_OUT_SERVER_UNREACHABLE -> loggedOutOfflineText"))
    }

    @Test
    fun flechaConsultaElBackGuardAntesDeNavegar() {
        val guard = source.indexOf("backGuard.intercept()")
        val navigation = source.indexOf("BackStep.Pop -> navController.popBackStack()")

        assertTrue(guard in 0 until navigation)
    }

    @Test
    fun proveeElBackGuardPorCompositionLocal() {
        assertTrue(source.contains("LocalBackGuard provides backGuard"))
    }

    @Test
    fun tituloDeRegistro() {
        assertTrue(source.contains("AppDestination.REGISTER -> stringResource(R.string.register_title)"))
    }

    @Test
    fun tituloDeVerificarCorreo() {
        assertTrue(source.contains("AppDestination.VERIFY_EMAIL -> stringResource(R.string.verify_title)"))
    }
}
