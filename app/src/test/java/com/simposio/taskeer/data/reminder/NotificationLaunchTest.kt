package com.simposio.taskeer.data.reminder

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationLaunchTest {

    @Test
    fun extra_tieneUnNombreEstable() {
        assertEquals(
            "com.simposio.taskeer.extra.OPEN_NOTIFICATIONS_VIEWER",
            NotificationLaunch.EXTRA_OPEN_VIEWER,
        )
    }

    @Test
    fun extra_viveEnDataReminderYNoEnUi() {
        val root = sourcesRoot()

        assertTrue(File(root, "data/reminder/NotificationLaunch.kt").isFile)
        val uiDeclarations = File(root, "ui").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("EXTRA_OPEN_VIEWER =") }
        assertFalse(uiDeclarations.any())
    }

    @Test
    fun notificador_adjuntaElExtraAlIntentDeApertura() {
        val notifier = File(sourcesRoot(), "data/reminder/NotificationReminderNotifier.kt").readText()

        assertTrue(notifier.contains(".putExtra(NotificationLaunch.EXTRA_OPEN_VIEWER, true)"))
    }

    private fun sourcesRoot(): File {
        val relativePath = "app/src/main/java/com/simposio/taskeer"
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.isDirectory }
            ?: error("No encontré $relativePath subiendo desde $userDir")
    }
}
