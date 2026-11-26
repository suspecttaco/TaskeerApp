package com.simposio.taskeer.ui.settings

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSettingsSectionSourceTest {

    private val source = MainSources.kotlin("ui/settings/AccountSettingsSection.kt")

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
            "model.security.forEach",
            "Spacing.MinTouchTarget",
            "clickable(onClick = onClick)",
            "actions.onChangePassword",
            "actions.onRegenerateCodes",
        ).forEach { assertTrue("falta $it", source.contains(it)) }
    }
}
