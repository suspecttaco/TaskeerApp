package com.simposio.taskeer.di

import com.simposio.taskeer.data.local.ReminderCandidateRow
import com.simposio.taskeer.data.local.ReminderTaskStateRow
import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.data.reminder.ReminderReconciler
import com.simposio.taskeer.data.repository.BoardRepository
import com.simposio.taskeer.data.repository.ColumnRepository
import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.data.repository.ReminderRepository
import com.simposio.taskeer.data.repository.SubtaskRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.testing.InMemoryCheckpointStore
import com.simposio.taskeer.testing.RecordingNotifier
import com.simposio.taskeer.testing.RecordingReminderScheduler
import com.simposio.taskeer.ui.tasks.RemindersViewModel
import com.simposio.taskeer.ui.tasks.SubtasksViewModel
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppContainerFactoryTest {

    @Test
    fun elConteoDeTareasPorColumna_usaElDaoDeTareas() = runTest {
        val dao = FakeTaskDao()
        dao.put(taskEntity("t1", columnId = "columna-1"))
        dao.put(taskEntity("t2", columnId = "columna-1"))
        dao.put(taskEntity("t3", columnId = "columna-2"))
        val count = taskCountByColumn(dao)

        assertEquals(2, count("columna-1"))
        assertEquals(1, count("columna-2"))
        assertEquals(0, count("columna-3"))
        assertEquals(listOf("columna-1", "columna-2", "columna-3"), dao.countedColumns)
    }

    @Test
    fun factory_construyeSubtasksViewModel() {
        val container = AppContainer(
            preferencesRepository = stub<PreferencesRepository>(),
            timeZoneProvider = { java.time.ZoneOffset.UTC },
            boardRepository = stub<BoardRepository>(),
            columnRepository = stub<ColumnRepository>(),
            taskRepository = stub<TaskRepository>(),
            subtaskRepository = stub<SubtaskRepository>(),
            myDayRepository = stub<MyDayRepository>(),
            reminderRepository = stub<ReminderRepository>(),
            reminderReconciler = reconciler(),
        )

        val viewModel = AppViewModelFactory(container).create(SubtasksViewModel::class.java)

        assertEquals(SubtasksViewModel::class.java, viewModel.javaClass)
    }

    @Test
    fun factory_construyeRemindersViewModel() {
        val container = AppContainer(
            preferencesRepository = stub<PreferencesRepository>(),
            timeZoneProvider = { java.time.ZoneOffset.UTC },
            boardRepository = stub<BoardRepository>(),
            columnRepository = stub<ColumnRepository>(),
            taskRepository = stub<TaskRepository>(),
            subtaskRepository = stub<SubtaskRepository>(),
            myDayRepository = stub<MyDayRepository>(),
            reminderRepository = stub<ReminderRepository>(),
            reminderReconciler = reconciler(),
        )

        val viewModel = AppViewModelFactory(container).create(RemindersViewModel::class.java)

        assertEquals(RemindersViewModel::class.java, viewModel.javaClass)
    }

    private fun reconciler() = ReminderReconciler(
        tasks = stub<TaskRepository>(),
        reminders = stub<ReminderRepository>(),
        preferences = stub<PreferencesRepository>(),
        checkpoints = InMemoryCheckpointStore(),
        scheduler = RecordingReminderScheduler(),
        notifier = RecordingNotifier(),
        timeZone = { java.time.ZoneOffset.UTC },
        clock = { java.time.Instant.EPOCH },
    )

    private inline fun <reified T : Any> stub(): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { _, method, _ -> error("${method.name} no se usa en este test") } as T

    private class FakeTaskDao : TaskDao() {
        private val rows = MutableStateFlow<Map<String, TaskEntity>>(emptyMap())

        val countedColumns: MutableList<String> = mutableListOf()

        fun put(task: TaskEntity) {
            rows.value = rows.value + (task.id to task)
        }

        override fun observeByBoard(boardId: String): Flow<List<TaskEntity>> = MutableStateFlow(emptyList())

        override suspend fun findActiveByColumn(columnId: String): List<TaskEntity> = emptyList()

        override suspend fun findById(id: String): TaskEntity? = rows.value[id]

        override suspend fun countByColumn(columnId: String): Int {
            countedColumns += columnId
            return rows.value.values.count { it.columnId == columnId }
        }

        override suspend fun upsert(task: TaskEntity) {
            rows.value = rows.value + (task.id to task)
        }

        override suspend fun upsertAll(tasks: List<TaskEntity>) {
            rows.value = rows.value + tasks.associateBy { it.id }
        }

        override suspend fun softDeleteSubtasksOfTask(taskId: String, deletedAt: Long) = Unit

        override fun observeReminderCandidates(): Flow<List<ReminderCandidateRow>> = emptyFlow()

        override suspend fun countActive(): Int = 0

        override suspend fun findReminderState(taskId: String): ReminderTaskStateRow? = null

        override suspend fun dismissRemindersOfTask(taskId: String, now: Long): Int = 0
    }

    private fun taskEntity(id: String, columnId: String): TaskEntity = TaskEntity(
        id = id,
        columnId = columnId,
        title = "Tarea $id",
        description = null,
        priority = "MEDIUM",
        dueAt = null,
        assigneeId = null,
        position = 0,
        createdAt = 0L,
        updatedAt = 0L,
        pending = true,
        deletedAt = null,
    )
}
