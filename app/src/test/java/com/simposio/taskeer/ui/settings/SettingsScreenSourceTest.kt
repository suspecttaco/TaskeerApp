package com.simposio.taskeer.ui.settings

import com.simposio.taskeer.testing.MainSources
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
    fun elReenvioSeCreaConSuPropioViewModel() {
        val source = MainSources.kotlin("ui/settings/SettingsScreen.kt")

        assertTrue(source.contains("ResendVerificationViewModel"))
        assertTrue(source.contains("onResendVerification = resendViewModel::resend"))
    }
}
