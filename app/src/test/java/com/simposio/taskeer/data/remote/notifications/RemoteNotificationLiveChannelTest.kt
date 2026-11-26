package com.simposio.taskeer.data.remote.notifications

import com.simposio.taskeer.data.remote.ApiResult
import com.simposio.taskeer.data.remote.AuthMode
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.remote.StreamResult
import com.simposio.taskeer.data.remote.TEST_CONFIG
import com.simposio.taskeer.data.remote.TransportFailureKind
import com.simposio.taskeer.data.remote.errorBody
import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.notification.InboxOutcome
import com.simposio.taskeer.domain.notification.LiveChannelState
import com.simposio.taskeer.testing.FakeApiClient
import com.simposio.taskeer.testing.FakeNotificationInboxRepository
import com.simposio.taskeer.testing.FakeStreamTransport
import com.simposio.taskeer.testing.FakeStreamTransport.Step
import com.simposio.taskeer.testing.apiOk
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteNotificationLiveChannelTest {

    private val client = FakeApiClient()
    private val transport = FakeStreamTransport()
    private val inbox = FakeNotificationInboxRepository()
    private val session = MutableStateFlow<SessionState>(signedIn("ana@example.com"))
    private var tickets = 0

    init {
        client.fallback = { request ->
            if (request.path == NotificationsApiContract.STREAM_TICKET_PATH) apiOk("""{"ticket":"${ticket(++tickets)}"}""") else apiOk()
        }
    }

    private fun signedIn(email: String): SessionState = SessionState.SignedIn(AccountSummary(email, "Ana"), false)

    private fun ticket(n: Int) = n.toString(16).padStart(64, '0')

    private fun urlOf(n: Int) = "https://api.test/api/v1/notifications/stream?ticket=${ticket(n)}"

    private fun uid(n: Int) = "00000000-0000-4000-8000-%012d".format(n)

    private fun notificationEvent(n: Int) = listOf(
        "event: notification",
        """data: {"id":"${uid(n)}","type":"MENTION","title":"Aviso","body":null,"readAt":null,"createdAt":"2026-11-26T09:00:00Z"}""",
        "",
    )

    private val open = listOf(": open")

    private fun TestScope.channel() = RemoteNotificationLiveChannel(
        client, transport, inbox, session, TEST_CONFIG,
        clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
        random = { 0.0 },
    )

    private fun TestScope.start(channel: RemoteNotificationLiveChannel = channel()): Job {
        val job = backgroundScope.launch { channel.run() }
        runCurrent()
        return job
    }

    private fun ticketPosts() = client.requests.count { it.path == NotificationsApiContract.STREAM_TICKET_PATH }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun sinSesion_noPideTicketNiAbreFlujo() = runTest {
        session.value = SessionState.SignedOut()

        channel().run()

        assertTrue(client.requests.isEmpty())
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun conSesion_pideTicketYAbreElFlujoSinAuthorization() = runTest {
        transport.enqueue(Step.Lines(open))

        start()

        val post = client.requests.first()
        assertEquals(HttpMethod.POST, post.method)
        assertEquals("notifications/stream-ticket", post.path)
        assertEquals(AuthMode.BEARER, post.auth)
        val get = transport.requests.single()
        assertEquals(HttpMethod.GET, get.method)
        assertEquals(urlOf(1), get.url)
        assertEquals("text/event-stream", get.headers["Accept"])
        assertFalse(get.headers.keys.any { it.equals("Authorization", ignoreCase = true) })
    }

    @Test
    fun cancelar_cierraElFlujo() = runTest {
        transport.enqueue(Step.Lines(open))
        val channel = channel()
        val job = start(channel)

        job.cancel()
        runCurrent()

        assertEquals(1, transport.cancelled)
        assertEquals(0, transport.active)
        assertEquals(LiveChannelState.STOPPED, channel.state.value)
    }

    @Test
    fun signedOut_detieneSinNuevosTickets() = runTest {
        transport.enqueue(Step.Lines(open))
        val channel = channel()
        val job = start(channel)

        session.value = SessionState.SignedOut()
        runCurrent()
        advance(120_000)

        assertTrue(job.isCompleted)
        assertEquals(1, ticketPosts())
        assertEquals(LiveChannelState.STOPPED, channel.state.value)
    }

    @Test
    fun resuming_noCierraElCanal() = runTest {
        transport.enqueue(Step.Lines(open))
        val job = start()

        session.value = SessionState.Resuming
        runCurrent()

        assertTrue(job.isActive)
        assertEquals(0, transport.cancelled)
    }

    @Test
    fun cambioDeCuenta_reabreConTicketNuevo() = runTest {
        transport.enqueue(Step.Lines(open), Step.Lines(open))
        start()

        session.value = signedIn("otra@example.com")
        runCurrent()

        assertEquals(listOf(urlOf(1), urlOf(2)), transport.requests.map { it.url })
        assertEquals(1, transport.cancelled)
        assertEquals(1, transport.active)
    }

    @Test
    fun ticketConsumido_401_pideTicketNuevoYNoReusaElViejo() = runTest {
        transport.enqueue(Step.Reject(401, errorBody("INVALID_STREAM_TICKET")), Step.Lines(open))
        start()
        advance(2_000)

        assertEquals(2, ticketPosts())
        assertEquals(listOf(urlOf(1), urlOf(2)), transport.requests.map { it.url })
    }

    @Test
    fun ticketMalFormado_noAbreElFlujo() = runTest {
        client.enqueue(apiOk("""{"ticket":"no-es-hex"}"""))
        val channel = channel()
        start(channel)

        assertTrue(transport.requests.isEmpty())
        assertEquals(LiveChannelState.WAITING, channel.state.value)
        advance(2_000)
        assertEquals(1, transport.requests.size)
        assertEquals(2, ticketPosts())
    }

    @Test
    fun cadaConexionUsaUnTicketNuevo() = runTest {
        transport.enqueue(Step.Lines(open, tail = StreamResult.Ended), Step.Lines(open))
        start()
        advance(1_000)

        val urls = transport.requests.map { it.url }
        assertEquals(2, urls.size)
        assertFalse(urls[1].contains(ticket(1)))
        assertEquals(2, ticketPosts())
    }

    @Test
    fun postDeTicketUnauthenticated_paraElCanal() = runTest {
        client.enqueue(ApiResult.Failure(DomainError.Unauthenticated("UNAUTHENTICATED", "x")))
        val channel = channel()

        val job = start(channel)
        advance(120_000)

        assertTrue(job.isCompleted)
        assertEquals(1, ticketPosts())
        assertTrue(transport.requests.isEmpty())
        assertEquals(LiveChannelState.STOPPED, channel.state.value)
    }

    @Test
    fun postDeTicketCon429_respetaRetryAfter() = runTest {
        client.enqueue(ApiResult.Failure(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), retryAfterSeconds = 30))
        start()

        advance(29_999)
        assertEquals(1, ticketPosts())
        advance(1)
        assertEquals(2, ticketPosts())
    }

    @Test
    fun eventoNotification_llegaAApplyLive() = runTest {
        transport.enqueue(Step.Lines(open + notificationEvent(1)))

        start()

        assertEquals(listOf(uid(1)), inbox.liveApplied.map { it.id })
    }

    @Test
    fun comentariosYHeartbeat_seIgnoran() = runTest {
        transport.enqueue(Step.Lines(open + listOf(": heartbeat", "", ": heartbeat", "")))

        start()

        assertTrue(inbox.liveApplied.isEmpty())
        assertEquals(1, transport.active)
    }

    @Test
    fun eventoReauth_noRompe() = runTest {
        transport.enqueue(Step.Lines(open + listOf("event: reauth", "data: {}", "") + notificationEvent(2)))

        start()

        assertEquals(listOf(uid(2)), inbox.liveApplied.map { it.id })
        assertEquals(1, transport.requests.size)
        assertEquals(1, transport.active)
    }

    @Test
    fun eventoReauth_noRompeYElCierreReconecta() = runTest {
        transport.enqueue(
            Step.Lines(open + listOf("event: reauth", "data: {}", ""), tail = StreamResult.Ended),
            Step.Lines(open),
        )

        start()
        advance(1_000)

        assertEquals(2, transport.requests.size)
        assertTrue(inbox.liveApplied.isEmpty())
    }

    @Test
    fun jsonRoto_seDescartaYSigue() = runTest {
        val broken = listOf("event: notification", "data: {roto", "")
        transport.enqueue(Step.Lines(open + broken + notificationEvent(3)))

        start()

        assertEquals(listOf(uid(3)), inbox.liveApplied.map { it.id })
        assertEquals(1, transport.active)
    }

    @Test
    fun eventoNotification_marcaElEstadoConectado() = runTest {
        transport.enqueue(Step.Lines(open))
        val channel = channel()
        assertEquals(LiveChannelState.STOPPED, channel.state.value)

        start(channel)

        assertEquals(LiveChannelState.CONNECTED, channel.state.value)
    }

    @Test
    fun cierreLimpio_reconectaConTicketNuevo() = runTest {
        transport.enqueue(Step.Lines(open, tail = StreamResult.Ended), Step.Lines(open))
        start()
        advance(1_000)

        assertEquals(listOf(urlOf(1), urlOf(2)), transport.requests.map { it.url })
    }

    @Test
    fun cierreLimpio_pausaMinimaDe1s() = runTest {
        transport.enqueue(Step.Lines(open, tail = StreamResult.Ended), Step.Lines(open))
        start()

        advance(999)
        assertEquals(1, transport.requests.size)
        advance(1)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun errorDeRed_reconectaConEspera() = runTest {
        transport.enqueue(Step.Fail(TransportFailureKind.UNREACHABLE), Step.Lines(open))
        start()

        advance(1_999)
        assertEquals(1, transport.requests.size)
        advance(1)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun fallosSeguidos_aumentanLaEspera() = runTest {
        transport.enqueue(
            Step.Fail(TransportFailureKind.IO),
            Step.Fail(TransportFailureKind.IO),
            Step.Lines(open),
        )
        start()

        advance(2_000)
        assertEquals(2, transport.requests.size)
        advance(3_999)
        assertEquals(2, transport.requests.size)
        advance(1)
        assertEquals(3, transport.requests.size)
    }

    @Test
    fun conexionEstable_reiniciaLaEspera() = runTest {
        transport.enqueue(
            Step.Fail(TransportFailureKind.IO),
            Step.Fail(TransportFailureKind.IO),
            Step.Lines(open, holdMillis = 10_000, tail = StreamResult.Failure(TransportFailureKind.TIMEOUT)),
            Step.Lines(open),
        )
        start()
        advance(6_000)
        assertEquals(3, transport.requests.size)

        advance(11_999)
        assertEquals(3, transport.requests.size)
        advance(1)
        assertEquals(4, transport.requests.size)
    }

    @Test
    fun lecturaSinDatos_esFalloYReconecta() = runTest {
        transport.enqueue(
            Step.Lines(open, tail = StreamResult.Failure(TransportFailureKind.TIMEOUT)),
            Step.Lines(open),
        )
        val channel = channel()
        start(channel)

        assertEquals(LiveChannelState.WAITING, channel.state.value)
        advance(2_000)
        assertEquals(2, transport.requests.size)
        assertEquals(LiveChannelState.CONNECTED, channel.state.value)
    }

    @Test
    fun nuncaHayDosConexionesSimultaneas() = runTest {
        transport.enqueue(
            Step.Lines(open, tail = StreamResult.Ended),
            Step.Lines(open, tail = StreamResult.Ended),
            Step.Lines(open),
        )
        val channel = channel()
        backgroundScope.launch { channel.run() }
        backgroundScope.launch { channel.run() }
        runCurrent()

        advance(5_000)
        session.value = signedIn("otra@example.com")
        advance(5_000)

        assertEquals(1, transport.maxActive)
        assertTrue(transport.requests.size >= 3)
    }

    @Test
    fun alConectar_pideNoLeidasSinIntervalo() = runTest {
        transport.enqueue(Step.Lines(open))

        start()

        assertEquals(1, inbox.catchUpCalls)
        assertEquals(0, inbox.refreshUnreadCalls)
    }

    @Test
    fun fallaLaPuestaAlDia_elCanalSigueAbierto() = runTest {
        inbox.result = InboxOutcome.Failed(DomainError.Server("INTERNAL_SERVER_ERROR", "x"))
        transport.enqueue(Step.Lines(open))
        val channel = channel()

        val job = start(channel)

        assertEquals(1, inbox.catchUpCalls)
        assertTrue(job.isActive)
        assertEquals(LiveChannelState.CONNECTED, channel.state.value)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun laPuestaAlDia_ocurreDespuesDeAbrirElFlujo() = runTest {
        var streamsOpenWhenCatchingUp = -1
        inbox.onCatchUp = { streamsOpenWhenCatchingUp = transport.requests.size }
        transport.enqueue(Step.Lines(open))

        start()

        assertEquals(1, streamsOpenWhenCatchingUp)
    }

    @Test
    fun transicionesDeEstado_stoppedConnectingConnectedWaiting() = runTest {
        transport.enqueue(
            Step.Lines(open, tail = StreamResult.Failure(TransportFailureKind.IO)),
            Step.Lines(open),
        )
        val channel = channel()
        val seen = mutableListOf<LiveChannelState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { channel.state.collect { seen += it } }

        val job = start(channel)
        advance(2_000)
        job.cancel()
        runCurrent()

        assertEquals(
            listOf(
                LiveChannelState.STOPPED,
                LiveChannelState.CONNECTING,
                LiveChannelState.CONNECTED,
                LiveChannelState.WAITING,
                LiveChannelState.CONNECTING,
                LiveChannelState.CONNECTED,
                LiveChannelState.STOPPED,
            ),
            seen,
        )
    }

    @Test
    fun elTicketNoApareceEnElTextoDeLasPeticionesNiDelResultado() = runTest {
        transport.enqueue(Step.Reject(401, errorBody("INVALID_STREAM_TICKET")))
        start()

        val shown = transport.requests.joinToString { it.toString() } + StreamResult.Rejected(401, emptyMap(), "x").toString()

        assertFalse(shown.contains(ticket(1)))
        assertNotEquals("", shown)
    }
}
