package com.simposio.taskeer.architecture

import com.simposio.taskeer.domain.auth.GoogleCredential
import com.simposio.taskeer.domain.auth.RecoveryCodes
import com.simposio.taskeer.testing.MainSources
import com.simposio.taskeer.ui.auth.LoginUiState
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleSecretsRulesTest {

    private val directories = listOf("data/auth/google", "data/remote/auth", "domain/auth", "ui/auth")
    private val persistenceOrLogs = Regex(
        "SavedStateHandle|rememberSaveable|\\bBundle\\b|datastore|SharedPreferences|androidx\\.room|\\bLog\\b|println|printStackTrace",
        RegexOption.IGNORE_CASE,
    )

    @Test
    fun tokenNonceYCodigosNoApareceEnLineasConPersistenciaOLogs() {
        val secret = Regex("idToken|nonce|recoveryCodes|GoogleCredential", RegexOption.IGNORE_CASE)
        val offenders = directories.flatMap { MainSources.kotlinFiles(it) }.flatMap { file ->
            file.readLines()
                .filter { secret.containsMatchIn(it) && persistenceOrLogs.containsMatchIn(it) }
                .map { "${file.name}: ${it.trim()}" }
        }

        assertTrue("secretos junto a persistencia o logs: $offenders", offenders.isEmpty())
    }

    @Test
    fun losUiStateNoTienenCamposSecretos() {
        val offenders = LoginUiState::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .filter { field ->
                val secretName = listOf("token", "nonce", "credential", "code", "password").any { field.name.contains(it, ignoreCase = true) }
                val secretType = field.type == RecoveryCodes::class.java || field.type == GoogleCredential::class.java ||
                    field.type == List::class.java
                (secretName && field.name != "passwordResetCount") || secretType
            }
            .map { it.name }

        assertTrue("campos secretos en LoginUiState: $offenders", offenders.isEmpty())
    }

    @Test
    fun lasPantallasNoUsanRememberSaveableNiSavedStateHandleConSecretos() {
        val files = MainSources.kotlinFiles("ui/auth")
        val offenders = files.flatMap { file -> file.readLines().map { file.name to it.trim() } }
            .filter { (_, line) -> line.contains("rememberSaveable") || line.contains("savedState.") || line.contains("savedState[") }
            .filterNot { (_, line) -> line.contains("KEY_CODES_PENDING") }

        assertTrue("estado persistente en ui/auth: $offenders", offenders.isEmpty())
        val viewModel = MainSources.kotlin("ui/auth/LoginViewModel.kt")
        assertTrue(viewModel.contains("savedState.get<Boolean>(KEY_CODES_PENDING)"))
        assertTrue(viewModel.contains("savedState[KEY_CODES_PENDING] = true"))
    }

    @Test
    fun elClienteNoDecideCuentas() {
        val claims = Regex("email|claims|jwt|decode|\\bsub\\b|displayName", RegexOption.IGNORE_CASE)
        val offenders = MainSources.kotlinFiles("data/auth/google").flatMap { file ->
            file.readLines().filter { claims.containsMatchIn(it) }.map { "${file.name}: ${it.trim()}" }
        }

        assertTrue("el cliente interpreta el token: $offenders", offenders.isEmpty())
        val repository = MainSources.kotlin("data/remote/auth/RemoteAuthRepository.kt")
        val google = repository.substringAfter("override suspend fun signInWithGoogle").substringBefore("// No toma `authMutex` ni toca `AuthSession`: registrar")
        assertFalse(google.contains("credential.idToken.") || google.contains("decode"))
    }

    @Test
    fun elViewModelNoNombraContextNiActivity() {
        val source = MainSources.kotlin("ui/auth/LoginViewModel.kt")

        listOf("Context", "Activity").forEach { assertFalse("nombra $it", Regex("\\b$it\\b").containsMatchIn(source.lines().filterNot { l -> l.trim().startsWith("//") }.joinToString("\n"))) }
    }

    @Test
    fun elManifiestoSoloCambiaPorQueriesJustificado() {
        val manifest = MainSources.manifest()

        assertFalse(manifest.contains("GET_ACCOUNTS"))
        assertFalse(manifest.contains("USE_CREDENTIALS"))
        assertFalse(manifest.contains("android:scheme"))
        assertEquals(6, Regex("<uses-permission").findAll(manifest).count())
        assertFalse(manifest.contains("<queries>"))
    }
}
