package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import com.simposio.taskeer.ui.verifyemail.ResendVerificationUiState
import com.simposio.taskeer.ui.verifyemail.VerifyEmailUiState
import java.lang.reflect.Modifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifyEmailSecretsRulesTest {

    private val directories = listOf("ui/verifyemail", "data/remote/auth", "domain/auth")
    private val sensitiveSinks = listOf(
        "Log.", "println(", "printStackTrace", "DataStore", "SharedPreferences", "edit()", "writeText", "Dao", "savedState",
    )

    @Test
    fun elTokenNoApareceEnLineasConPersistenciaOLogs() {
        val offenders = directories.flatMap { MainSources.kotlinFiles(it) }.flatMap { file ->
            file.readLines().filter { line ->
                val lower = line.lowercase()
                (lower.contains("token") || lower.contains("rawinput")) && sensitiveSinks.any { line.contains(it) }
            }.map { "${file.name}: ${it.trim()}" }
        }

        assertTrue("token junto a persistencia o logs: $offenders", offenders.isEmpty())
    }

    @Test
    fun verifyEmailUiStateNoTieneCamposSecretos() {
        val forbidden = listOf("token", "code", "link", "input", "raw", "text")
        val offenders = listOf(VerifyEmailUiState::class.java, ResendVerificationUiState::class.java)
            .flatMap { type -> type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) } }
            .filter { field -> forbidden.any { field.name.contains(it, ignoreCase = true) } }
            .map { it.name }

        assertTrue("campos secretos: $offenders", offenders.isEmpty())
    }

    @Test
    fun verifyEmailNoUsaRememberSaveableNiSavedStateHandle() {
        val offenders = MainSources.kotlinFiles("ui/verifyemail").filter { file ->
            val text = file.readText()
            text.contains("rememberSaveable") || text.contains("SavedStateHandle")
        }.map { it.name }

        assertTrue("estado guardado en verifyemail: $offenders", offenders.isEmpty())
    }

    @Test
    fun laRutaDeVerificarNoLlevaArgumentos() {
        val destination = MainSources.kotlin("ui/navigation/AppDestination.kt")

        assertTrue(destination.contains("VERIFY_EMAIL(\"verify-email\")"))
        assertFalse(MainSources.kotlin("ui/navigation/AppNavHost.kt").contains("navArgument(\"token\""))
    }

    @Test
    fun elManifiestoNoDeclaraEnlacesProfundos() {
        val manifest = MainSources.manifest()

        assertFalse(manifest.contains("android.intent.action.VIEW"))
        assertFalse(manifest.contains("android:scheme"))
        assertFalse(manifest.contains("autoVerify"))
    }

    @Test
    fun soloElPuenteNombraDataRemote() {
        val offenders = MainSources.kotlinFiles("").filter { file ->
            val path = file.invariantSeparatorsPath
            !path.contains("/data/remote/") && !path.endsWith("/data/auth/AuthRepositoryFactory.kt") &&
                file.readText().lines().any { it.startsWith("import com.simposio.taskeer.data.remote") }
        }.map { it.name }

        assertTrue("fuera del puente: $offenders", offenders.isEmpty())
    }
}
