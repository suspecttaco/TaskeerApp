package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import com.simposio.taskeer.ui.accountsecurity.ChangePasswordUiState
import com.simposio.taskeer.ui.accountsecurity.RegenerateCodesUiState
import com.simposio.taskeer.ui.passwordreset.ResetPasswordUiState
import java.io.File
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordResetSecretsRulesTest {

    private val directories = listOf("ui/passwordreset", "ui/accountsecurity", "data/remote/auth", "domain/auth")
    private val sinks = Regex(
        "SavedStateHandle|rememberSaveable|\\bBundle\\b|datastore|SharedPreferences|androidx\\.room|\\bLog\\b|println|printStackTrace|writeText",
        RegexOption.IGNORE_CASE,
    )
    private val secretName = Regex("password|\\bcode\\b|currentPassword|newPassword|recoveryCodes", RegexOption.IGNORE_CASE)

    @Test
    fun contrasenasYCodigoNoApareceEnLineasConPersistenciaOLogs() {
        val offenders = directories.flatMap { MainSources.kotlinFiles(it) }.flatMap { file ->
            file.readLines()
                .filter { line ->
                    val code = line.trim()
                    !code.startsWith("//") && secretName.containsMatchIn(code) && sinks.containsMatchIn(code) &&
                        !code.contains("KEY_CODES_PENDING")
                }
                .map { "${file.name}: ${it.trim()}" }
        }

        assertTrue("secretos junto a persistencia o logs: $offenders", offenders.isEmpty())
    }

    @Test
    fun losUiStateNoTienenCamposSecretos() {
        val offenders = listOf(
            ResetPasswordUiState::class.java,
            ChangePasswordUiState::class.java,
            RegenerateCodesUiState::class.java,
        ).flatMap { type -> type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) } }
            .filter { field ->
                listOf("password", "code", "token", "secret").any {
                    field.name.contains(it, ignoreCase = true)
                } && field.name != "secretsWiped"
            }
            .map { it.name }

        assertTrue("campos secretos en UiState: $offenders", offenders.isEmpty())
    }

    @Test
    fun lasPantallasNoUsanRememberSaveableNiSavedStateHandleConSecretos() {
        val offenders = (MainSources.kotlinFiles("ui/passwordreset") + MainSources.kotlinFiles("ui/accountsecurity"))
            .filter { it.name.endsWith("Screen.kt") }
            .filter { file ->
                val text = file.readText()
                text.contains("rememberSaveable") || text.contains("SavedStateHandle") || text.contains("rememberTextFieldState")
            }
            .map { it.name }

        assertTrue("estado persistente en pantallas de secretos: $offenders", offenders.isEmpty())
    }

    @Test
    fun soloRegenerateGuardaCodesPendingEnSavedState() {
        val offenders = (MainSources.kotlinFiles("ui/passwordreset") + MainSources.kotlinFiles("ui/accountsecurity"))
            .filter { it.name.endsWith("ViewModel.kt") && it.name != "RegenerateCodesViewModel.kt" }
            .filter { it.readText().contains("SavedStateHandle") }
            .map { it.name }

        assertTrue("SavedStateHandle en ViewModels de secretos: $offenders", offenders.isEmpty())
    }

    @Test
    fun soloElPuenteNombraDataRemote() {
        val root = MainSources.kotlinRoot
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.startsWith(File(root, "data/remote")) }
            .filter { file -> file.readText().let { it.contains("data.remote") || it.contains("data/remote") } }
            .map { it.relativeTo(root).path }
            .toList()

        assertEquals(listOf("data/auth/AuthRepositoryFactory.kt"), offenders)
    }

    @Test
    fun elManifiestoNoDeclaraCambios() {
        val manifest = MainSources.manifest()

        listOf("WRITE_EXTERNAL_STORAGE", "READ_EXTERNAL_STORAGE", "FileProvider", "regenerate", "reset-password")
            .forEach { assertFalse("sobra $it", manifest.contains(it)) }
    }
}
