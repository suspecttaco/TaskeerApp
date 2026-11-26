package com.simposio.taskeer.domain.notification

import com.simposio.taskeer.testing.MainSources
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerNotificationTest {

    private fun notification(readAt: Instant?) = ServerNotification(
        id = "1",
        type = ServerNotificationType.REMINDER,
        title = "Entregar la memoria",
        body = "Cuerpo secreto",
        taskId = null,
        createdAt = Instant.EPOCH,
        readAt = readAt,
    )

    @Test
    fun isUnread_conReadAtNulo_esTrue() {
        assertTrue(notification(null).isUnread)
    }

    @Test
    fun isUnread_conReadAt_esFalse() {
        assertFalse(notification(Instant.EPOCH).isUnread)
    }

    @Test
    fun toString_noIncluyeTituloNiCuerpo() {
        val text = notification(null).toString()

        assertFalse(text.contains("Entregar la memoria") || text.contains("Cuerpo secreto"))
    }

    @Test
    fun sinDependenciasDeAndroid() {
        val files = listOf("ServerNotification.kt", "InboxState.kt", "InboxRules.kt")
        val violations = files.flatMap { name ->
            MainSources.kotlin("domain/notification/$name").lines()
                .filter { it.startsWith("import ") }
                .filter { line -> listOf("android.", "androidx.", "data.", "kotlinx.coroutines").any { line.contains(it) } }
        }

        assertTrue("domain/notification debe ser Kotlin puro: $violations", violations.isEmpty())
    }
}
