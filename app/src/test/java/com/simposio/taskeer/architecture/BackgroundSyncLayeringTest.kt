package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundSyncLayeringTest {

    private val adapters = setOf("WorkManagerBackgroundScheduler.kt", "BackgroundSyncWorker.kt")

    @Test
    fun androidxWork_soloEnLosDosAdaptadores() {
        val importing = MainSources.kotlinFiles("")
            .filter { file -> file.readLines().any { it.trim().startsWith("import androidx.work") } }
            .map { it.name }
            .toSet()

        assertEquals(adapters, importing)
    }

    @Test
    fun androidxWork_soloEnDataSync() {
        val outside = MainSources.kotlinFiles("")
            .filter { it.name in adapters }
            .map { it.parentFile.name }
            .toSet()

        assertEquals(setOf("sync"), outside)
    }

    @Test
    fun dominioYUi_noReferencianLosAdaptadores() {
        val offenders = (MainSources.kotlinFiles("domain") + MainSources.kotlinFiles("ui") + MainSources.kotlinFiles("di"))
            .filter { file -> adapters.any { adapter -> file.readText().contains(adapter.removeSuffix(".kt")) } }
            .map { it.name }
            .filterNot { it == "AppContainerFactory.kt" }

        assertTrue("referencian los adaptadores: $offenders", offenders.isEmpty())
    }

    @Test
    fun catalogo_unaSolaEntradaAndroidxWork() {
        val catalog = findUp("gradle/libs.versions.toml").readLines()

        assertEquals(1, catalog.count { it.contains("group = \"androidx.work\"") })
    }

    @Test
    fun buildGradle_usaWorkRuntimeSinKtx() {
        val gradle = findUp("app/build.gradle.kts").readText()

        assertTrue(gradle.contains("libs.androidx.work.runtime"))
        assertFalse(gradle.contains("work.runtime.ktx"))
    }

    @Test
    fun worker_sinNotificacionesNiPrimerPlano() {
        val worker = MainSources.kotlin("data/sync/BackgroundSyncWorker.kt")

        listOf("NotificationManager", "ForegroundInfo", "setExpedited", "setForeground").forEach {
            assertFalse("el worker referencia $it", worker.contains(it))
        }
    }

    @Test
    fun solo_unaClaseInvocaPushYPullDelTransporte() {
        val callers = MainSources.kotlinFiles("data/sync")
            .filter { Regex("""transport\.(push|pull)\(""").containsMatchIn(it.readText()) }
            .map { it.name }

        assertEquals(listOf("SyncEngine.kt"), callers)
    }

    @Test
    fun interruptorDeAjustes_soloConSesion() {
        val screen = MainSources.kotlin("ui/settings/SettingsScreen.kt")

        assertTrue(screen.contains("if (syncState.visible) {\n            BackgroundSyncRow("))
    }

    private fun findUp(relative: String): File {
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }.map { File(it, relative) }.first { it.isFile }
    }
}
