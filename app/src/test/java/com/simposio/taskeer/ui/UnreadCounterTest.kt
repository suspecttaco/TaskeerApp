package com.simposio.taskeer.ui

import com.simposio.taskeer.domain.model.NotificationEvent
import com.simposio.taskeer.testing.FakeNotificationHistoryRepository
import com.simposio.taskeer.ui.notifications.UnreadCounterViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnreadCounterTest {

    private val history = FakeNotificationHistoryRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun contador_sigueLlegadaLecturaYBorrado() = runTest {
        val viewModel = UnreadCounterViewModel(history)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.unreadCount.collect {} }
        assertEquals(0, viewModel.unreadCount.value)

        history.record(NotificationEvent.Digest(1), true)
        history.record(NotificationEvent.Digest(2), true)
        assertEquals(2, viewModel.unreadCount.value)

        history.markRead(history.all.value.first().id)
        assertEquals(1, viewModel.unreadCount.value)

        history.delete(history.all.value.last().id)
        assertEquals(0, viewModel.unreadCount.value)
    }

    @Test
    fun contador_alMarcarTodasYBorrarTodo_quedaACero() = runTest {
        val viewModel = UnreadCounterViewModel(history)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.unreadCount.collect {} }
        repeat(3) { history.record(NotificationEvent.Digest(it), true) }

        history.markAllRead()
        assertEquals(0, viewModel.unreadCount.value)

        history.record(NotificationEvent.Digest(9), true)
        history.deleteAll()
        assertEquals(0, viewModel.unreadCount.value)
    }
}
