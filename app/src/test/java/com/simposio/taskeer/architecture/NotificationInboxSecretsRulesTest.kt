package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationInboxSecretsRulesTest {

    @Test
    fun sinLogsDeContenido() {
        val files = MainSources.kotlinFiles("data/remote/notifications") +
            MainSources.kotlinFiles("ui/notifications") +
            MainSources.kotlinFiles("domain/notification")
        val logging = Regex("""\bLog\.[a-z]\(|\bprintln\(|\bprint\(|Timber\.|printStackTrace""")

        val offenders = files.filter { logging.containsMatchIn(it.readText()) }.map { it.name }

        assertTrue("las notificaciones pueden contener títulos de tareas ajenas; sin logs: $offenders", offenders.isEmpty())
        assertTrue(files.isNotEmpty())
    }

    @Test
    fun serverNotification_noInterpolaTituloNiCuerpoEnSuTexto() {
        val source = MainSources.kotlin("domain/notification/ServerNotification.kt")
        val toString = source.substringAfter("override fun toString()")

        assertTrue(!toString.contains("\$title") && !toString.contains("\$body"))
    }
}
