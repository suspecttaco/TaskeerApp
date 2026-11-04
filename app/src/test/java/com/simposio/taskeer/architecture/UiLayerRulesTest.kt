package com.simposio.taskeer.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class UiLayerRulesTest {

    private val sourcesRoot: File = resolveSourcesRoot()

    @Test
    fun notificationsUi_sinDao() {
        val forbidden = listOf(
            "data.local",
            "AppNotificationDao",
            "AppNotificationEntity",
            "AppNotificationMapping",
            "NotificationHistoryRepositoryImpl",
            "androidx.room",
        )
        val files = File(sourcesRoot, "ui/notifications").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        assertTrue("debe existir la UI del visor", files.isNotEmpty())
        val violations = files.flatMap { file ->
            forbidden.filter { file.readText().contains(it) }
                .map { "${file.relativeTo(sourcesRoot)} referencia $it" }
        }
        assertTrue("la UI del visor usa el contrato; violaciones: $violations", violations.isEmpty())
    }

    private fun resolveSourcesRoot(): File {
        val relativePath = "app/src/main/java/com/simposio/taskeer"
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.isDirectory }
            ?: error("No encontré $relativePath subiendo desde $userDir")
    }
}
