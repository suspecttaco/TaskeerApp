package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.remote.notifications.RemoteNotificationInboxRepository
import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.notification.LiveChannelState
import com.simposio.taskeer.domain.notification.ServerNotification
import com.simposio.taskeer.domain.notification.ServerNotificationType
import com.simposio.taskeer.testing.FakeApiClient
import com.simposio.taskeer.testing.FakeNotificationInboxRepository
import com.simposio.taskeer.testing.FakeNotificationLiveChannel
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveRoleChangeSignalsTest {

    private val inbox = FakeNotificationInboxRepository()
    private val channel = FakeNotificationLiveChannel()

    private fun notification(id: String, type: ServerNotificationType) = ServerNotification(
        id = id,
        type = type,
        title = "Titulo",
        body = "Cuerpo",
        taskId = null,
        createdAt = Instant.parse("2026-11-26T09:00:00Z"),
        readAt = null,
    )

    private fun TestScope.collect(signals: RoleChangeSignals): List<Unit> {
        val received = mutableListOf<Unit>()
        backgroundScope.launch { signals.signals.collect { received += it } }
        runCurrent()
        return received
    }

    @Test
    fun roleChanged_emiteUnaSenal() = runTest {
        val received = collect(LiveRoleChangeSignals(inbox, channel))

        inbox.applyLive(notification("n1", ServerNotificationType.ROLE_CHANGED))
        runCurrent()

        assertEquals(1, received.size)
    }

    @Test
    fun otroTipo_noEmite() = runTest {
        val received = collect(LiveRoleChangeSignals(inbox, channel))

        ServerNotificationType.entries.filter { it != ServerNotificationType.ROLE_CHANGED }.forEachIndexed { i, type ->
            inbox.applyLive(notification("n$i", type))
        }
        runCurrent()

        assertTrue(received.isEmpty())
    }

    @Test
    fun reconexion_emiteUnaSenal() = runTest {
        channel.mutableState.value = LiveChannelState.CONNECTED
        val received = collect(LiveRoleChangeSignals(inbox, channel))

        channel.mutableState.value = LiveChannelState.WAITING
        runCurrent()
        channel.mutableState.value = LiveChannelState.CONNECTING
        runCurrent()
        channel.mutableState.value = LiveChannelState.CONNECTED
        runCurrent()

        assertEquals(1, received.size)
    }

    @Test
    fun primeraConexion_noEmite() = runTest {
        val received = collect(LiveRoleChangeSignals(inbox, channel))

        channel.mutableState.value = LiveChannelState.CONNECTING
        runCurrent()
        channel.mutableState.value = LiveChannelState.CONNECTED
        runCurrent()

        assertTrue(received.isEmpty())
    }

    @Test
    fun sinSuscriptor_noAcumula() = runTest {
        val signals = LiveRoleChangeSignals(inbox, channel)
        inbox.applyLive(notification("n1", ServerNotificationType.ROLE_CHANGED))
        runCurrent()

        val received = collect(signals)

        assertTrue(received.isEmpty())
    }

    @Test
    fun despuesDeSignedOut_noEmite() = runTest {
        val session = MutableStateFlow<SessionState>(SessionState.SignedIn(AccountSummary("ana@example.com", "Ana"), false))
        val remoteInbox = RemoteNotificationInboxRepository(FakeApiClient(), session, backgroundScope)
        val received = collect(LiveRoleChangeSignals(remoteInbox, channel))

        session.value = SessionState.SignedOut()
        runCurrent()
        remoteInbox.applyLive(notification("n1", ServerNotificationType.ROLE_CHANGED))
        runCurrent()

        assertTrue(received.isEmpty())
    }
}
