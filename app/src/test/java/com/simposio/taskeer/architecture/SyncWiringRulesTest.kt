package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncWiringRulesTest {

    private val permissionPattern = Regex("<uses-permission[^>]*android:name=\"([^\"]+)\"")

    @Test
    fun manifest_declaraAccessNetworkState_yNingunPermisoMasDeSync() {
        val permissions = permissionPattern.findAll(MainSources.manifest()).map { it.groupValues[1] }.toSet()

        assertEquals(
            setOf(
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.READ_CALENDAR",
                "android.permission.WRITE_CALENDAR",
            ),
            permissions,
        )
    }

    @Test
    fun manifestFuente_sinServiciosNiWorkManager() {
        assertFalse(MainSources.manifest().contains("<service"))
        assertFalse(MainSources.manifest().contains("androidx.work"))
    }

    @Test
    fun soloLasFuentesDeAndroidTocanAndroidEnDataSync() {
        val allowed = setOf("AndroidConnectivitySource.kt", "WorkManagerBackgroundScheduler.kt", "BackgroundSyncWorker.kt")
        val offenders = MainSources.kotlinFiles("data/sync")
            .filter { file -> file.readLines().any { it.trim().startsWith("import android.") || it.trim().startsWith("import androidx.") } }
            .map { it.name }
            .filterNot { it in allowed }

        assertTrue("data/sync con Android fuera de las fuentes permitidas: $offenders", offenders.isEmpty())
    }

    @Test
    fun existeUnaSolaLlamadaASyncEngineRun() {
        val calls = MainSources.kotlinFiles("").filter { it.readText().contains("sync.run()") }.map { it.name }

        assertEquals(listOf("AppContainerFactory.kt"), calls)
    }

    @Test
    fun elMotorRecibeElRetrocesoCompuesto() {
        val factory = MainSources.kotlin("di/AppContainerFactory.kt")

        assertTrue(factory.contains("retry = CompositeRetryScheduler(triggers, BackgroundRetryScheduler("))
    }

    @Test
    fun taskeerApplication_noImportaWorkManager() {
        assertFalse(MainSources.kotlin("TaskeerApplication.kt").contains("androidx.work"))
    }

    private fun findUp(relative: String): File {
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }.map { File(it, relative) }.first { it.isFile }
    }
}
