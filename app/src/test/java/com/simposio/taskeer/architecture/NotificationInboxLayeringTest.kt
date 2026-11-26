package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationInboxLayeringTest {

    private val inboxFiles = MainSources.kotlinFiles("data/remote/notifications") +
        MainSources.kotlinFiles("domain/notification").filter { it.name in DOMAIN_FILES } +
        MainSources.kotlinFiles("data/repository").filter { it.name == "NotificationInboxRepository.kt" }

    @Test
    fun noUsaRoomNiDataStore() {
        val violations = inboxFiles.flatMap { file ->
            listOf("androidx.room", "androidx.datastore", "data.local", "SharedPreferences")
                .filter { file.readText().contains(it) }
                .map { "${file.name} referencia $it" }
        }

        assertTrue("la bandeja del servidor vive solo en memoria: $violations", violations.isEmpty())
        assertTrue(inboxFiles.size >= 6)
    }

    @Test
    fun contratoNoExponeTiposRemotos() {
        val contract = MainSources.kotlin("data/repository/NotificationInboxRepository.kt")

        assertTrue(!contract.contains("data.remote") && !contract.contains("org.json") && !contract.contains("android."))
    }

    @Test
    fun laInterfazDeUsuarioNoConoceLaCapaRemotaNiLocal() {
        val violations = MainSources.kotlinFiles("ui/notifications").flatMap { file ->
            listOf("data.remote", "data.local").filter { file.readText().contains(it) }.map { "${file.name} referencia $it" }
        }

        assertTrue("ui/notifications no debe conocer data.remote ni data.local: $violations", violations.isEmpty())
    }

    private companion object {
        val DOMAIN_FILES = setOf("ServerNotification.kt", "InboxState.kt", "InboxRules.kt")
    }
}
