package com.simposio.taskeer.ui.notifications

import com.simposio.taskeer.domain.model.NotificationEvent
import com.simposio.taskeer.domain.notification.InboxState
import com.simposio.taskeer.testing.FakeNotificationHistoryRepository
import com.simposio.taskeer.testing.FakeNotificationInboxRepository
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

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): UnreadCounterViewModel {
        val viewModel = UnreadCounterViewModel(history, inbox)
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
}
