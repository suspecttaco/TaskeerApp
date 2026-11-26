package com.simposio.taskeer.architecture

import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.remote.HttpRequest
import com.simposio.taskeer.data.remote.StreamResult
import com.simposio.taskeer.data.remote.notifications.RemoteNotificationLiveChannel
import com.simposio.taskeer.data.remote.TEST_CONFIG
import com.simposio.taskeer.testing.FakeApiClient
import com.simposio.taskeer.testing.FakeNotificationInboxRepository
import com.simposio.taskeer.testing.FakeStreamTransport
import com.simposio.taskeer.testing.MainSources
import kotlinx.coroutines.flow.MutableStateFlow
import com.simposio.taskeer.domain.auth.SessionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationLiveSecretsRulesTest {

    private val files = listOf(
        "data/repository/NotificationLiveChannel.kt",
        "data/remote/StreamTransport.kt",
        "data/remote/transport/OkHttpStreamTransport.kt",
        "data/remote/notifications/SseParser.kt",
        "data/remote/notifications/RemoteNotificationLiveChannel.kt",
        "domain/notification/LiveChannelState.kt",
        "domain/notification/LiveReconnectRules.kt",
    )

    @Test
    fun sinLogsNiSalidaEstandar() {
        val logging = Regex("""\bLog\.[a-z]\(|\bprintln\(|\bprint\(|Timber\.|printStackTrace""")

        val offenders = files.filter { logging.containsMatchIn(MainSources.kotlin(it)) }

        assertTrue("el ticket y los eventos no se registran: $offenders", offenders.isEmpty())
    }

    @Test
    fun toStringDeLasPeticionesYRechazosNoLlevaElTicketNiElCuerpo() {
        val ticket = "a".repeat(64)
        val request = HttpRequest(HttpMethod.GET, "https://api.test/notifications/stream?ticket=$ticket", emptyMap(), null)
        val rejected = StreamResult.Rejected(401, mapOf("x" to ticket), "cuerpo-$ticket")

        assertFalse(request.toString().contains(ticket))
        assertFalse(rejected.toString().contains(ticket) || rejected.toString().contains("cuerpo"))
    }

    @Test
    fun elCanalNoDefineToStringNiGuardaElTicket() {
        val source = MainSources.kotlin("data/remote/notifications/RemoteNotificationLiveChannel.kt")
        val channel = RemoteNotificationLiveChannel(
            FakeApiClient(), FakeStreamTransport(), FakeNotificationInboxRepository(),
            MutableStateFlow<SessionState>(SessionState.SignedOut()), TEST_CONFIG,
        )

        assertFalse(source.contains("override fun toString"))
        assertFalse(source.contains("var ticket") || source.contains("private val ticket"))
        assertTrue(channel.toString().startsWith("com.simposio.taskeer.data.remote.notifications.RemoteNotificationLiveChannel@"))
    }

    @Test
    fun elTicketNoEntraEnNavegacionNiEstadoGuardado() {
        val offenders = MainSources.kotlinFiles("ui").filter { file ->
            file.readText().let { it.contains("ticket", ignoreCase = true) && it.contains("stream", ignoreCase = true) }
        }.map { it.name }

        assertTrue("la interfaz no maneja tickets de stream: $offenders", offenders.isEmpty())
    }
}
