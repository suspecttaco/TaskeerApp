package com.simposio.taskeer.di

import com.simposio.taskeer.data.reminder.ReminderReconciler
import com.simposio.taskeer.data.repository.BoardRepository
import com.simposio.taskeer.data.repository.ColumnRepository
import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.data.repository.PlannedRepository
import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.data.repository.ReminderRepository
import com.simposio.taskeer.data.repository.SubtaskRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.testing.FakeDestinationTaskRepository
import com.simposio.taskeer.testing.FakeNotificationHistoryRepository
import com.simposio.taskeer.testing.FakeTaskListRepository
import com.simposio.taskeer.testing.InMemoryCheckpointStore
import com.simposio.taskeer.testing.RecordingNotifier
import com.simposio.taskeer.testing.RecordingReminderScheduler
import com.simposio.taskeer.ui.inbox.InboxViewModel
import com.simposio.taskeer.ui.lists.TaskListsViewModel
import com.simposio.taskeer.ui.quickadd.QuickAddViewModel
import com.simposio.taskeer.ui.tasks.MoveToListViewModel
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppViewModelFactoryTest {

    private val lists = FakeTaskListRepository()

    @Test
    fun crea_taskListsViewModelYMoveToListViewModel() {
        val factory = AppViewModelFactory(container())

        assertEquals(TaskListsViewModel::class.java, factory.create(TaskListsViewModel::class.java).javaClass)
        assertEquals(MoveToListViewModel::class.java, factory.create(MoveToListViewModel::class.java).javaClass)
    }

    @Test
    fun crea_inboxYQuickAddConElRepositorioDeListas() {
        val factory = AppViewModelFactory(container())

        assertEquals(InboxViewModel::class.java, factory.create(InboxViewModel::class.java).javaClass)
        val quickAdd = factory.create(QuickAddViewModel::class.java)
        assertEquals(QuickAddViewModel::class.java, quickAdd.javaClass)
    }

    @Test
    fun taskListsViewModel_comparteLaSeleccionConElContenedor() {
        val container = container()
        val viewModel = AppViewModelFactory(container).create(TaskListsViewModel::class.java)

        val field = TaskListsViewModel::class.java.getDeclaredField("selection").apply { isAccessible = true }
        assertSame(container.listSelection, field.get(viewModel))
    }

    private fun container(): AppContainer = AppContainer(
        preferencesRepository = stub<PreferencesRepository>(),
        timeZoneProvider = { java.time.ZoneOffset.UTC },
        boardRepository = flowStub<BoardRepository>(),
        columnRepository = flowStub<ColumnRepository>(),
        taskRepository = FakeDestinationTaskRepository(),
        taskListRepository = lists,
        subtaskRepository = stub<SubtaskRepository>(),
        myDayRepository = flowStub<MyDayRepository>(),
        plannedRepository = stub<PlannedRepository>(),
        reminderRepository = stub<ReminderRepository>(),
        reminderReconciler = ReminderReconciler(
            tasks = stub<TaskRepository>(),
            reminders = stub<ReminderRepository>(),
            preferences = stub<PreferencesRepository>(),
            checkpoints = InMemoryCheckpointStore(),
            scheduler = RecordingReminderScheduler(),
            notifier = RecordingNotifier(),
            timeZone = { java.time.ZoneOffset.UTC },
            clock = { java.time.Instant.EPOCH },
        ),
        notificationHistoryRepository = FakeNotificationHistoryRepository(),
    )

    private inline fun <reified T : Any> stub(): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { _, method, _ -> error("${method.name} no se usa en este test") } as T

    private inline fun <reified T : Any> flowStub(): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { _, method, _ ->
        if (method.returnType == Flow::class.java) emptyFlow<Any>() else error("${method.name} no se usa en este test")
    } as T
}
