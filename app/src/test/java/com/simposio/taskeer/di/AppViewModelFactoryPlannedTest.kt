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
import com.simposio.taskeer.testing.FakeNotificationHistoryRepository
import com.simposio.taskeer.testing.FakeTaskListRepository
import com.simposio.taskeer.testing.InMemoryCheckpointStore
import com.simposio.taskeer.testing.RecordingNotifier
import com.simposio.taskeer.testing.RecordingReminderScheduler
import com.simposio.taskeer.ui.planned.PlannedViewModel
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class AppViewModelFactoryPlannedTest {

    @Test
    fun factory_construyePlannedViewModel() {
        val planned = Proxy.newProxyInstance(
            PlannedRepository::class.java.classLoader,
            arrayOf(PlannedRepository::class.java),
        ) { _, method, _ ->
            if (method.returnType == Flow::class.java) emptyFlow<Any>() else error("${method.name} no se usa")
        } as PlannedRepository
        val container = AppContainer(
            preferencesRepository = stub<PreferencesRepository>(),
            timeZoneProvider = { java.time.ZoneOffset.UTC },
            boardRepository = stub<BoardRepository>(),
            columnRepository = stub<ColumnRepository>(),
            taskRepository = stub<TaskRepository>(),
            taskListRepository = FakeTaskListRepository(),
            subtaskRepository = stub<SubtaskRepository>(),
            myDayRepository = stub<MyDayRepository>(),
            plannedRepository = planned,
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

        val viewModel = AppViewModelFactory(container).create(PlannedViewModel::class.java)

        assertEquals(PlannedViewModel::class.java, viewModel.javaClass)
    }

    private inline fun <reified T : Any> stub(): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { _, method, _ -> error("${method.name} no se usa en este test") } as T
}
