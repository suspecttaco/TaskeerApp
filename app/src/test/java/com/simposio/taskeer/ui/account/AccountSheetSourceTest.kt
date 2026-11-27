package com.simposio.taskeer.ui.account

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSheetSourceTest {

    private val source = MainSources.kotlin("ui/account/AccountSheet.kt")
    private val host = MainSources.kotlin("ui/account/AccountSheetHost.kt")

    @Test
    fun esUnModalBottomSheetNativo() {
        assertTrue(source.contains("ModalBottomSheet("))
        assertTrue(source.contains("skipPartiallyExpanded = true"))
    }

    @Test
    fun mostrarEstadoYAccionesDeVerificacionConComponentesNativos() {
        listOf(
            "R.string.verify_account_unverified",
            "R.string.verify_account_verified",
            "R.string.verify_resend_action",
            "R.string.verify_have_link",
            "TextButton(",
            "ListItem(",
        ).forEach { assertTrue("falta $it", source.contains(it)) }
    }

    @Test
    fun elResultadoDelReenvioSeAnunciaConLiveRegion() {
        assertTrue(source.contains("liveRegion = LiveRegionMode.Polite"))
    }

    @Test
    fun elReenvioSeBloqueaMientrasEnviaOPorLimite() {
        assertTrue(source.contains("enabled = resend.canResend"))
    }

    @Test
    fun noGuardaEstadoDeVerificacionEnSaveable() {
        assertFalse(source.contains("rememberSaveable"))
    }

    @Test
    fun seguridad_muestraFilasClicablesConAreaMinimaDeToque() {
        listOf(
            "R.string.chpw_settings_row",
            "R.string.regen_settings_row",
            "model.section.security.forEach",
            "Spacing.MinTouchTarget",
            "clickable(onClick = onClick)",
            "actions.onChangePassword",
            "actions.onRegenerateCodes",
        ).forEach { assertTrue("falta $it", source.contains(it)) }
    }

    @Test
    fun cerrarSesionPideConfirmacionConElDialogoExistente() {
        listOf("AlertDialog(", "R.string.auth_logout_confirm_title", "R.string.auth_logout_confirm_message", "actions.onSignOut()")
            .forEach { assertTrue("falta $it", source.contains(it)) }
    }

    @Test
    fun noNavegaAlCerrarSesion() {
        val signOut = host.substringAfter("onSignOut = {").substringBefore("},")

        assertTrue(signOut.contains("session.logout()"))
        assertTrue(signOut.contains("onDismiss()"))
        assertFalse(signOut.contains("navigate"))
    }

    @Test
    fun lasAccionesQueNavegan_cierranLaHojaAntes() {
        val leave = host.substringAfter("fun leaveTo(").substringBefore("AccountSheet(")

        assertTrue(leave.indexOf("onDismiss()") in 0 until leave.indexOf("navigate("))
        listOf("CHANGE_PASSWORD", "REGENERATE_CODES", "VERIFY_EMAIL").forEach { assertTrue(host.contains("leaveTo(AppDestination.$it)")) }
    }
}
