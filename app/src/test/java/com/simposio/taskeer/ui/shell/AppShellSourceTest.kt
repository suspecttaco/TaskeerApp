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
}
