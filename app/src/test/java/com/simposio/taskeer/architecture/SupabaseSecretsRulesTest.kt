package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseSecretsRulesTest {

    private val directories = listOf("ui", "data/remote/supabase", "data/remote/auth", "domain/auth")
    private val persistenceOrLogs = Regex(
        "SavedStateHandle|rememberSaveable|\\bBundle\\b|datastore|SharedPreferences|\\bLog\\b|println|printStackTrace|savedState",
        RegexOption.IGNORE_CASE,
    )
    private val secrets = Regex("idToken|id_token|supabaseAccessToken|anonKey|anon_key|access_token|apikey", RegexOption.IGNORE_CASE)

    @Test
    fun tokensYAnonKeyNoAparecenEnLineasConPersistenciaOLogs() {
        val offenders = directories.flatMap { MainSources.kotlinFiles(it) }.flatMap { file ->
            file.readLines()
                .filter { !it.trim().startsWith("import ") && secrets.containsMatchIn(it) && persistenceOrLogs.containsMatchIn(it) }
                .map { "${file.name}: ${it.trim()}" }
        }

        assertTrue("secretos junto a persistencia o logs: $offenders", offenders.isEmpty())
    }

    @Test
    fun ningunUiTieneElTokenNiLaAnonKey() {
        val offenders = MainSources.kotlinFiles("ui").filter { secrets.containsMatchIn(it.readText()) }.map { it.name }

        assertTrue("el token o la anon key llegan a ui/: $offenders", offenders.isEmpty())
    }

    @Test
    fun elSupabaseNoUsaLogNiPrintln() {
        val offenders = MainSources.kotlinFiles("data/remote/supabase")
            .filter { Regex("android\\.util\\.Log|\\bLog\\.|println").containsMatchIn(it.readText()) }

        assertTrue(offenders.map { it.name }.toString(), offenders.isEmpty())
    }
}
