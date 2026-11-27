package com.simposio.taskeer.ui.settings

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsScreenSourceTest {

    @Test
    fun cuenta_esLaPrimeraSeccion() {
        val source = MainSources.kotlin("ui/settings/SettingsScreen.kt")
        val account = source.indexOf("AccountSettingsSection(")

        assertTrue(account > 0)
        listOf("ThemeSettingsSection(", "RegionSettingsSection(", "ReminderSettingsSection(").forEach {
            assertTrue("Cuenta debe ir antes de $it", account < source.indexOf(it))
        }
    }

    @Test
    fun noCreaViewModelDeReenvio_niNavegaASeguridad() {
        val source = MainSources.kotlin("ui/settings/SettingsScreen.kt")

        assertFalse(source.contains("ResendVerificationViewModel"))
        listOf("onChangePassword", "onRegenerateCodes", "onHaveVerificationLink", "onSignOut").forEach {
            assertFalse("sobra $it", source.contains(it))
        }
        assertTrue(source.contains("onOpenAccount = onOpenAccount"))
    }
}
