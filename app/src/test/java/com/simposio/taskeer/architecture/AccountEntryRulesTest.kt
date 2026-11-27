package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountEntryRulesTest {

    private fun code(path: String): String =
        MainSources.kotlin(path).lines().filterNot { it.trim().startsWith("//") }.joinToString("\n")

    @Test
    fun avatarRulesSinAndroidNiDataNiUi() {
        val source = code("domain/auth/AvatarRules.kt")

        listOf("android.", "androidx.", "com.simposio.taskeer.data", "com.simposio.taskeer.ui").forEach {
            assertFalse("importa $it", source.contains("import $it"))
        }
    }

    @Test
    fun ultimaHojaSoloUnaVez() {
        val instantiators = MainSources.kotlinFiles("ui")
            .filter { Regex("(?<![A-Za-z])AccountSheet\\(").containsMatchIn(it.readText()) }
            .map { it.name }
            .filterNot { it == "AccountSheet.kt" }

        assertEquals(listOf("AccountSheetHost.kt"), instantiators)
        assertEquals(1, Regex("AccountSheetHost\\(").findAll(code("ui/shell/AppShell.kt")).count())
    }

    @Test
    fun resendViewModelSoloEnUiAccount() {
        val users = MainSources.kotlinFiles("ui")
            .filter { it.readText().contains("ResendVerificationViewModel") }
            .map { "${it.parentFile?.name}/${it.name}" }
            .filterNot { it.startsWith("verifyemail/") }

        assertEquals(listOf("account/AccountSheetHost.kt"), users)
    }

    @Test
    fun uiAccountSinFotosNiLibreriasDeImagen() {
        MainSources.kotlinFiles("ui/account").forEach { file ->
            listOf("coil", "AsyncImage", "painterResource", "ImageBitmap", "picture", "avatarUrl").forEach {
                assertFalse("${file.name} usa $it", file.readText().contains(it))
            }
        }
    }

    @Test
    fun uiAccountNoImportaDataNiLlamaAAuth() {
        MainSources.kotlinFiles("ui/account").forEach { file ->
            val text = file.readText()
            val body = file.readLines().filterNot { it.startsWith("import ") }.joinToString("\n")
            assertFalse("${file.name} importa data", text.contains("import com.simposio.taskeer.data"))
            assertFalse("${file.name} llama a auth.", Regex("\\bauth\\.").containsMatchIn(body))
        }
    }

    @Test
    fun uiAccountNoOfreceGoogle() {
        MainSources.kotlinFiles("ui/account").forEach { file ->
            assertFalse(file.name, file.readText().contains("signInWithGoogle") || file.readText().contains("google_continue_action"))
        }
    }

    @Test
    fun appNavHostSinPopUpToSettingsLiteral() {
        val source = code("ui/navigation/AppNavHost.kt")

        assertFalse(source.contains("popUpTo(AppDestination.SETTINGS.route)"))
        assertEquals(3, Regex("popUpTo\\(flowAnchor\\(\\)\\)").findAll(source).count())
        assertTrue(source.contains("accountFlowAnchor("))
    }

    @Test
    fun shellTopBarPasaLaEntradaSoloEnHub() {
        val shell = code("ui/shell/AppShell.kt")

        assertTrue(shell.contains("if (chrome.topBar.showsAccountEntry()) accountEntryFor(sessionState) else null"))
        assertTrue(code("ui/shell/SectionTopBar.kt").contains("accountEntry?.let { AccountEntryAction("))
    }

    // El spec decía v15; la base ya estaba en v17 al implementar: esta feature no la toca.
    @Test
    fun esquemaIntacto() {
        val database = code("data/local/AppDatabase.kt")

        assertTrue(Regex("version = 17\\b").containsMatchIn(database))
    }

    @Test
    fun settingsYaNoTieneSeguridadNiLogout() {
        val settings = MainSources.kotlinFiles("ui/settings").joinToString("\n") { it.readText() }

        listOf("AccountSecurityAction", "auth_logout", "verify_resend", "chpw_settings_row", "regen_settings_row", "session::logout")
            .forEach { assertFalse("Ajustes aún contiene $it", settings.contains(it)) }
    }
}
