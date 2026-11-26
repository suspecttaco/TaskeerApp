package com.simposio.taskeer.architecture

import com.simposio.taskeer.data.repository.AuthRepository
import com.simposio.taskeer.domain.auth.RecoveryCodes
import com.simposio.taskeer.testing.MainSources
import com.simposio.taskeer.ui.register.RegisterUiState
import java.io.File
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoverySecretsRulesTest {

    private val codeDirectories = listOf("ui/register", "ui/accountsecurity", "data/remote/auth", "domain/auth")
    private val persistenceOrLogs = Regex(
        "SavedStateHandle|rememberSaveable|\\bBundle\\b|datastore|SharedPreferences|androidx\\.room|\\bLog\\b|println",
        RegexOption.IGNORE_CASE,
    )

    @Test
    fun losCodigosNoApareceneEnLineasConPersistenciaOLogs() {
        val offenders = codeDirectories.flatMap { MainSources.kotlinFiles(it) }.flatMap { file ->
            file.readLines()
                .filter { (it.contains("RecoveryCodes") || it.contains("recoveryCodes")) && persistenceOrLogs.containsMatchIn(it) }
                .map { "${file.name}: ${it.trim()}" }
        }

        assertTrue("códigos junto a persistencia o logs: $offenders", offenders.isEmpty())
    }

    @Test
    fun soloSeGuardaCodesPendingEnSavedStateHandle() {
        val offenders = (MainSources.kotlinFiles("ui/register") + MainSources.kotlinFiles("ui/accountsecurity"))
            .flatMap { file -> file.readLines().map { file.name to it.trim() } }
            .filter { (_, line) -> line.contains("savedState.") || line.contains("savedState[") }
            .filterNot { (_, line) -> line.contains("KEY_CODES_PENDING") }

        assertTrue("usos de SavedStateHandle ajenos a codes_pending: $offenders", offenders.isEmpty())
        val viewModel = MainSources.kotlin("ui/register/RegisterViewModel.kt")
        assertTrue(viewModel.contains("savedState.get<Boolean>(KEY_CODES_PENDING)"))
        assertTrue(viewModel.contains("savedState[KEY_CODES_PENDING] = true"))
    }

    @Test
    fun registerUiStateNoTieneCamposSecretos() {
        val offenders = RegisterUiState::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .filter { field ->
                val secretName = listOf("code", "password", "token").any { field.name.contains(it, ignoreCase = true) }
                val secretType = field.type == RecoveryCodes::class.java || field.type == List::class.java
                (secretName && field.name != "passwordResetCount") || secretType
            }
            .map { it.name }

        assertTrue("campos secretos en RegisterUiState: $offenders", offenders.isEmpty())
    }

    @Test
    fun recoveryCodesNoEsDataClass() {
        val methods = RecoveryCodes::class.java.declaredMethods.map { it.name }

        assertFalse(methods.contains("copy"))
        assertFalse(methods.contains("component1"))
    }

    @Test
    fun elContratoNoDevuelveTokens() {
        val returnTypes = AuthRepository::class.java.methods.map { it.returnType.simpleName.lowercase() }

        assertTrue(returnTypes.none { it.contains("token") })
        assertEquals(0, AuthRepository::class.java.methods.count { it.name.contains("token", ignoreCase = true) })
    }

    @Test
    fun elManifiestoNoPideAlmacenamiento() {
        val manifest = File(MainSources.kotlinRoot, "../../../../AndroidManifest.xml").readText()

        listOf("WRITE_EXTERNAL_STORAGE", "READ_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE", "FileProvider")
            .forEach { assertFalse("sobra $it", manifest.contains(it)) }
    }

    @Test
    fun registerNoUsaRememberSaveableNiRememberTextFieldState() {
        val offenders = MainSources.kotlinFiles("ui/register")
            .filter { it.readText().contains("rememberSaveable") || it.readText().contains("rememberTextFieldState") }
            .map { it.name }

        assertTrue("estado persistente en ui/register: $offenders", offenders.isEmpty())
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
}
