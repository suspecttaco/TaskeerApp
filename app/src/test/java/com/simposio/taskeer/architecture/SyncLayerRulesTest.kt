package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncLayerRulesTest {

    private val appDir: File = MainSources.kotlinRoot.parentFile.parentFile.parentFile.parentFile.parentFile.parentFile

    private fun imports(file: File): List<String> = file.readLines().filter { it.trim().startsWith("import ") }

    @Test
    fun dataSync_noImportaUi() {
        val offenders = MainSources.kotlinFiles("data/sync").filter { file -> imports(file).any { it.contains(".ui.") } }

        assertTrue(offenders.map { it.name }.toString(), offenders.isEmpty())
    }

    @Test
    fun domainSync_sinAndroid() {
        val offenders = MainSources.kotlinFiles("domain/sync").filter { file ->
            imports(file).any { it.contains("import android.") || it.contains("import androidx.") }
        }

        assertTrue(offenders.map { it.name }.toString(), offenders.isEmpty())
    }

    @Test
    fun soloElPuenteNombraDataRemote() {
        val offenders = MainSources.kotlinRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> imports(file).any { it.contains("data.remote.sync") } }
            .map { it.relativeTo(MainSources.kotlinRoot).path }
            .toList()

        assertEquals(listOf("data/auth/AuthRepositoryFactory.kt"), offenders.filterNot { it.startsWith("data/remote/") })
    }

    @Test
    fun sinCodigoDelPuente() {
        val sources = File(appDir, "src").walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "kts") && "/build/" !in it.path }
            .filterNot { it.name == "SyncLayerRulesTest.kt" }
        val offenders = sources.filter { file ->
            val text = file.readText()
            text.contains("RestBridge") || text.contains("BridgeTransport") || text.contains("TransportMode")
        }.map { it.name }.toList()

        assertTrue(offenders.toString(), offenders.isEmpty())
        assertFalse(File(appDir, "src/debug/java/com/simposio/taskeer/data/remote/sync").exists())
        assertFalse(File(appDir, "src/release/java").exists())
        assertFalse(File(appDir, "build.gradle.kts").readText().contains("syncTransport"))
    }
}
