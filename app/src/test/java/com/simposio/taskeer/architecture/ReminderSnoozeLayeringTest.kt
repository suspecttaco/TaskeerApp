package com.simposio.taskeer.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSnoozeLayeringTest {

    private val sourcesRoot: File = resolveSourcesRoot()

    private fun source(path: String): String = File(sourcesRoot, path).readText()

    @Test
    fun dominioDelSnooze_sinAndroidNiRoom() {
        listOf(
            "domain/reminder/ReminderRules.kt",
            "domain/reminder/SnoozeOption.kt",
            "domain/model/AppNotification.kt",
            "domain/notification/NotificationHistoryRules.kt",
        ).forEach { path ->
            listOf("android.", "androidx.", "data.local").forEach {
                assertFalse("$path referencia $it", source(path).contains(it))
            }
        }
    }

    @Test
    fun contratosDeRepositorio_neutros() {
        listOf(
            "data/repository/ReminderRepository.kt",
            "data/repository/NotificationHistoryRepository.kt",
        ).forEach { path ->
            listOf("androidx.room", "android.", "data.local").forEach {
                assertFalse("$path referencia $it", source(path).contains(it))
            }
        }
    }

    @Test
    fun visorDeAvisos_noTocaDaosNiRoom() {
        File(sourcesRoot, "ui/notifications").listFiles().orEmpty().forEach { file ->
            listOf("data.local", "androidx.room", "Dao").forEach {
                assertFalse("${file.name} referencia $it", file.readText().contains(it))
            }
        }
    }

    @Test
    fun posponer_noUsaElPlanificadorNiElConciliador() {
        listOf("data/repository/ReminderRepositoryImpl.kt", "ui/notifications/NotificationsViewModel.kt").forEach { path ->
            val text = source(path)
            assertFalse("$path usa el planificador", text.contains("ReminderScheduler"))
            assertFalse("$path usa el conciliador", text.contains("ReminderReconciler"))
        }
    }

    @Test
    fun dataSync_noConoceElSnooze() {
        val offenders = File(sourcesRoot, "data/sync").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.readText().contains("nooze") }
            .map { it.name }.toList()

        assertTrue("data/sync no debe conocer el snooze: $offenders", offenders.isEmpty())
    }

    @Test
    fun elPlanificadorNoConoceElSnooze() {
        assertFalse(source("data/repository/ReminderScheduler.kt").contains("nooze"))
        assertTrue(File(sourcesRoot, "data/reminder/ReminderReconciler.kt").readText().contains("nextPendingAt"))
    }

    private fun resolveSourcesRoot(): File {
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, "app/src/main/java/com/simposio/taskeer") }
            .firstOrNull { it.isDirectory }
            ?: error("No encontré las fuentes subiendo desde $userDir")
    }
}
