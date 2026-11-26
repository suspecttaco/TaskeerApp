package com.simposio.taskeer.ui.notifications

import com.simposio.taskeer.domain.model.NotificationEvent
import com.simposio.taskeer.domain.notification.InboxState
import com.simposio.taskeer.domain.notification.ServerNotification
import com.simposio.taskeer.domain.notification.ServerNotificationType
import java.time.Instant
import com.simposio.taskeer.testing.FakeNotificationHistoryRepository
import com.simposio.taskeer.testing.FakeNotificationInboxRepository
import com.simposio.taskeer.testing.FakeNotificationLiveChannel
import com.simposio.taskeer.testing.MainSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnreadCounterViewModelTest {

    private val history = FakeNotificationHistoryRepository()
    private val inbox = FakeNotificationInboxRepository()
    private val channel = FakeNotificationLiveChannel()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): UnreadCounterViewModel {
        val viewModel = UnreadCounterViewModel(history, inbox, channel)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.unreadCount.collect {} }
        return viewModel
    }

    @Test
    fun suma_localesYServidor() = runTest {
        val viewModel = viewModel()
        history.record(NotificationEvent.Digest(1), true)
        history.record(NotificationEvent.Digest(2), true)

        inbox.mutableState.value = InboxState(unreadCount = 3)

        assertEquals(5, viewModel.unreadCount.value)
    }

    @Test
    fun sinSesion_soloLocales() = runTest {
        val viewModel = viewModel()
        history.record(NotificationEvent.Digest(1), true)

        assertEquals(1, viewModel.unreadCount.value)
    }

    @Test
    fun cerrarSesion_quitaLaCuentaDelServidor() = runTest {
        val viewModel = viewModel()
        history.record(NotificationEvent.Digest(1), true)
        inbox.mutableState.value = InboxState(unreadCount = 4)
        assertEquals(5, viewModel.unreadCount.value)

        inbox.mutableState.value = InboxState()

        assertEquals(1, viewModel.unreadCount.value)
    }

    @Test
    fun alIniciar_consultaLasNoLeidas() = runTest {
        val viewModel = viewModel()

        viewModel.onAppForegrounded()

        assertEquals(1, inbox.refreshUnreadCalls)
    }

    @Test
    fun eventoEnVivo_subeElContadorTotal() = runTest {
        val viewModel = viewModel()
        history.record(NotificationEvent.Digest(1), true)
        inbox.mutableState.value = InboxState(unreadCount = 2)
        assertEquals(3, viewModel.unreadCount.value)

        inbox.applyLive(
            ServerNotification(
                id = "00000000-0000-4000-8000-000000000009",
                type = ServerNotificationType.MENTION,
                title = "Aviso",
                body = null,
                taskId = null,
                createdAt = Instant.parse("2026-11-26T09:00:00Z"),
                readAt = null,
            ),
        )

        assertEquals(4, viewModel.unreadCount.value)
    }

    @Test
    fun keepLiveWhileForeground_delegaEnElCanal() = runTest {
        val viewModel = viewModel()

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.keepLiveWhileForeground() }

        assertEquals(1, channel.runCalls)
        assertEquals(true, job.isActive)
        channel.release.complete(Unit)
        assertEquals(true, job.isCompleted)
    }

    @Test
    fun sinSesion_elCanalNoSeLanza() {
        val source = MainSources.kotlin("ui/app/TaskeerApp.kt")
        val effect = source.substringAfter("LaunchedEffect(signedIn, lifecycle)").substringBefore("TaskeerTheme(")

        assertEquals(true, effect.indexOf("if (signedIn)") in 0 until effect.indexOf("keepLiveWhileForeground"))
        assertEquals(true, effect.contains("repeatOnLifecycle(Lifecycle.State.STARTED)"))
    }
}
