package com.simposio.taskeer.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationHistoryLayeringTest {

    private val appRoot: File = resolveRoot("app")
    private val sourcesRoot: File = File(appRoot, "src/main/java/com/simposio/taskeer")

    @Test
    fun dominioDelHistorial_sinAndroidNiRoom() {
        val files = listOf(
            "domain/model/AppNotification.kt",
            "domain/notification/NotificationHistoryRules.kt",
        ).map { File(sourcesRoot, it) }

        val violations = files.flatMap { file ->
            listOf("android.", "androidx.", "data.local", "kotlinx.coroutines")
                .filter { file.readText().contains(it) }
                .map { "${file.relativeTo(sourcesRoot)} referencia $it" }
        }

        assertTrue("el dominio del historial debe ser Kotlin puro; violaciones: $violations", violations.isEmpty())
    }

    @Test
    fun historyRecordingNotifier_viveEnDataReminderYNoUsaAndroid() {
        val file = File(sourcesRoot, "data/reminder/HistoryRecordingNotifier.kt")

        assertTrue(file.isFile)
        val source = file.readText()
        assertFalse(source.contains("import android.") || source.contains("import androidx."))
        assertTrue(source.contains(": ReminderNotifier"))
        assertTrue(source.contains("private val history: NotificationHistoryRepository"))
    }

    @Test
    fun contratoDelHistorial_esNeutro() {
        val source = File(sourcesRoot, "data/repository/NotificationHistoryRepository.kt").readText()

        listOf("androidx.room", "android.", "data.local").forEach {
            assertFalse("el contrato no debe referenciar $it", source.contains(it))
        }
    }

    @Test
    fun noExistenDataRemoteNiDataSync() {
        assertFalse(File(sourcesRoot, "data/remote").exists())
        assertFalse(File(sourcesRoot, "data/sync").exists())
    }

    @Test
    fun elConciliador_noConoceElHistorial() {
        val reconciler = File(sourcesRoot, "data/reminder/ReminderReconciler.kt").readText()

        assertFalse(reconciler.contains("NotificationHistory"))
        assertFalse(reconciler.contains("HistoryRecordingNotifier"))
    }

    @Test
    fun appBuildGradle_mantieneLasDependenciasPrevias() {
        val gradle = File(appRoot, "build.gradle.kts").readText()
        val block = gradle.substring(gradle.indexOf("dependencies {"))
        val declared = block.lines().map { it.trim() }
            .filter { Regex("^\\w+\\(.*\\)$").matches(it) }
            .toSet()

        assertTrue("dependencias inesperadas: ${declared - EXPECTED_DEPENDENCIES}", declared == EXPECTED_DEPENDENCIES)
    }

    private fun resolveRoot(name: String): File {
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, name) }
            .firstOrNull { File(it, "src/main/java/com/simposio/taskeer").isDirectory }
            ?: error("No encontré $name subiendo desde $userDir")
    }

    private companion object {
        val EXPECTED_DEPENDENCIES: Set<String> = setOf(
            "implementation(platform(libs.androidx.compose.bom))",
            "implementation(libs.androidx.activity.compose)",
            "implementation(libs.androidx.compose.material3)",
            "implementation(libs.androidx.compose.ui)",
            "implementation(libs.androidx.compose.ui.graphics)",
            "implementation(libs.androidx.compose.ui.tooling.preview)",
            "implementation(libs.androidx.core.ktx)",
            "implementation(libs.androidx.datastore.preferences)",
            "implementation(libs.androidx.lifecycle.runtime.compose)",
            "implementation(libs.androidx.lifecycle.runtime.ktx)",
            "implementation(libs.androidx.lifecycle.viewmodel.compose)",
            "implementation(libs.androidx.lifecycle.viewmodel.ktx)",
            "implementation(libs.androidx.navigation.compose)",
            "implementation(libs.androidx.room.runtime)",
            "ksp(libs.androidx.room.compiler)",
            "coreLibraryDesugaring(libs.desugar.jdk.libs)",
            "testImplementation(libs.androidx.datastore.preferences.core)",
            "testImplementation(libs.junit)",
            "testImplementation(libs.kotlinx.coroutines.test)",
            "androidTestImplementation(platform(libs.androidx.compose.bom))",
            "androidTestImplementation(libs.androidx.compose.ui.test.junit4)",
            "androidTestImplementation(libs.androidx.espresso.core)",
            "androidTestImplementation(libs.androidx.junit)",
            "debugImplementation(libs.androidx.compose.ui.test.manifest)",
            "debugImplementation(libs.androidx.compose.ui.tooling)",
        )
    }
}
