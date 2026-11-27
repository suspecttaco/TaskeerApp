package com.simposio.taskeer.ui.settings

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSettingsSectionSourceTest {

    private val source = MainSources.kotlin("ui/settings/AccountSettingsSection.kt")

    @Test
    fun tieneUnaFilaClicableConAreaMinimaDeToque() {
        assertTrue(source.contains("ListItem("))
        assertTrue(source.contains("clickable(onClick = onOpenAccount)"))
        assertTrue(source.contains("clickable(onClick = onSignIn)"))
        assertTrue(source.contains("Spacing.MinTouchTarget"))
        assertTrue(source.contains("R.string.settings_account_row"))
    }

    @Test
    fun recibeSoloLosDosCallbacks() {
        val signature = source.substringAfter("internal fun AccountSettingsSection(").substringBefore(") {")

        assertEquals(listOf("state", "onSignIn", "onOpenAccount", "modifier"), Regex("(\\w+): ").findAll(signature).map { it.groupValues[1] }.toList())
    }

    @Test
    fun yaNoContieneVerificacionSeguridadNiCierreDeSesion() {
        listOf(
            "rememberSaveable",
            "verify_",
            "SecurityRow",
            "VerificationRows",
            "AlertDialog",
            "logout",
            "ResendVerification",
            "onChangePassword",
            "onRegenerateCodes",
            "RETRY",
        ).forEach { assertFalse("sobra $it", source.contains(it)) }
    }
}
