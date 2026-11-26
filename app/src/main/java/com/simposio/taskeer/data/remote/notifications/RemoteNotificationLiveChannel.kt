package com.simposio.taskeer.data.remote.notifications

import com.simposio.taskeer.data.remote.ApiClient
import com.simposio.taskeer.data.remote.ApiConfig
import com.simposio.taskeer.data.remote.ApiErrorMapper
import com.simposio.taskeer.data.remote.ApiRequest
import com.simposio.taskeer.data.remote.ApiResult
import com.simposio.taskeer.data.remote.AuthMode
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.remote.HttpRequest
import com.simposio.taskeer.data.remote.StreamResult
import com.simposio.taskeer.data.remote.StreamTransport
import com.simposio.taskeer.data.repository.NotificationInboxRepository
import com.simposio.taskeer.data.repository.NotificationLiveChannel
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.notification.LiveChannelState
import com.simposio.taskeer.domain.notification.LiveReconnectRules
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ThreadLocalRandom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RemoteNotificationLiveChannel(
    private val client: ApiClient,
    private val transport: StreamTransport,
    private val inbox: NotificationInboxRepository,
    private val sessionState: StateFlow<SessionState>,
    private val config: ApiConfig,
    private val clock: () -> Instant = Instant::now,
    private val random: () -> Double = { ThreadLocalRandom.current().nextDouble(-1.0, 1.0) },
) : NotificationLiveChannel {

    private sealed interface Cycle {
        data object Stop : Cycle

        class Next(val failures: Int) : Cycle
    }

    private val runLock = Mutex()
    private val mutableState = MutableStateFlow(LiveChannelState.STOPPED)

    override val state: StateFlow<LiveChannelState> = mutableState.asStateFlow()

    override suspend fun run() {
        runLock.withLock {
            if (sessionState.value !is SessionState.SignedIn) return
            try {
                var failures = 0
                while (sessionState.value !is SessionState.SignedOut) {
                    val email = (sessionState.value as? SessionState.SignedIn)?.account?.email
                    val next = whileSameAccount(email) { cycle(failures) } ?: Cycle.Next(0)
                    when (next) {
                        Cycle.Stop -> return
                        is Cycle.Next -> failures = next.failures
                    }
                }
            } finally {
                mutableState.value = LiveChannelState.STOPPED
            }
        }
    }

    // Cierra lo que esté en curso (conexión o espera) cuando la sesión termina o cambia de cuenta; null = se interrumpió.
    private suspend fun <T> whileSameAccount(email: String?, block: suspend CoroutineScope.() -> T): T? = coroutineScope {
        val work = async { block() }
        val watcher = launch {
            sessionState.first { !sameAccount(it, email) }
            work.cancel()
        }
        try {
            work.await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            null
        } finally {
            watcher.cancel()
        }
    }

    private fun sameAccount(session: SessionState, email: String?): Boolean = when (session) {
        is SessionState.SignedOut -> false
        SessionState.Resuming -> true
        is SessionState.SignedIn -> email == null || session.account?.email == null || session.account.email == email
    }

    private suspend fun CoroutineScope.cycle(failures: Int): Cycle {
        mutableState.value = LiveChannelState.CONNECTING
        val ticket = when (val result = client.execute(ApiRequest(HttpMethod.POST, NotificationsApiContract.STREAM_TICKET_PATH, auth = AuthMode.BEARER))) {
            is ApiResult.Failure ->
                return if (result.error is DomainError.Unauthenticated) Cycle.Stop else backoff(failures + 1, result.retryAfterSeconds)
            is ApiResult.Success ->
                NotificationsApiContract.parseTicket(result.response.body) ?: return backoff(failures + 1, null)
        }
        val request = HttpRequest(
            method = HttpMethod.GET,
            url = "${config.baseUrl}${NotificationsApiContract.STREAM_PATH}?ticket=$ticket",
            headers = mapOf("Accept" to "text/event-stream", "User-Agent" to config.userAgent),
            body = null,
        )
        val parser = SseParser()
        var openedAt: Instant? = null
        var gotEvent = false
        var catchUp: Job? = null
        val result = try {
            transport.stream(request) { line ->
                if (openedAt == null) {
                    openedAt = clock()
                    mutableState.value = LiveChannelState.CONNECTED
                    catchUp = launch { inbox.catchUp() }
                }
                for (event in parser.feed(line)) {
                    if (event.name != NOTIFICATION_EVENT) continue
                    val notification = NotificationsApiContract.parseOne(event.data) ?: continue
                    inbox.applyLive(notification)
                    gotEvent = true
                }
            }
        } finally {
            catchUp?.cancel()
        }
        val opened = openedAt
        val openMillis = if (opened == null) 0L else Duration.between(opened, clock()).toMillis()
        val stable = LiveReconnectRules.shouldResetFailures(openMillis, gotEvent)
        val base = if (stable) 0 else failures
        return when (result) {
            StreamResult.Ended ->
                if (opened != null) pause(base) else backoff(failures + 1, null)
            is StreamResult.Rejected ->
                backoff(failures + 1, ApiErrorMapper.map(result.status, result.headers, result.body).retryAfterSeconds)
            is StreamResult.Failure -> backoff(base + 1, null)
        }
    }

    private suspend fun pause(failures: Int): Cycle {
        mutableState.value = LiveChannelState.WAITING
        delay(LiveReconnectRules.CLEAN_RECONNECT_PAUSE_MILLIS)
        return Cycle.Next(failures)
    }

    private suspend fun backoff(failures: Int, retryAfterSeconds: Long?): Cycle {
        mutableState.value = LiveChannelState.WAITING
        delay(LiveReconnectRules.delayMillis(failures, retryAfterSeconds, random()))
        return Cycle.Next(failures)
    }

    private companion object {
        const val NOTIFICATION_EVENT = "notification"
    }
}
