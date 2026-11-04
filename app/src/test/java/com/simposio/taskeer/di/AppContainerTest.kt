package com.simposio.taskeer.di

import com.simposio.taskeer.data.reminder.ReminderReconciler
import com.simposio.taskeer.data.repository.BoardRepository
import com.simposio.taskeer.data.repository.ColumnRepository
import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.data.repository.ReminderRepository
import com.simposio.taskeer.data.repository.SubtaskRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.MyDay
import com.simposio.taskeer.domain.model.Subtask
import com.simposio.taskeer.domain.model.SubtaskDraft
import com.simposio.taskeer.domain.model.SubtaskProgress
import com.simposio.taskeer.domain.model.ReminderCandidate
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.testing.FakePreferencesRepository
import com.simposio.taskeer.testing.FakeReminderRepository
import com.simposio.taskeer.testing.InMemoryCheckpointStore
import com.simposio.taskeer.testing.RecordingNotifier
import com.simposio.taskeer.testing.RecordingReminderScheduler
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppContainerTest {

    private val auckland = ZoneId.of("Pacific/Auckland")

    @Test
    fun entregaLaImplementacionInyectada() {
        val repository = FakePreferencesRepository()

        val container = container(preferencesRepository = repository)

        assertSame(repository, container.preferencesRepository)
        assertEquals(auckland, container.timeZoneProvider.current())
    }

    @Test
    fun entregaElRepositorioDeTablerosInyectado() {
        val boards = FakeBoardRepository()

        val container = container(boardRepository = boards)

        assertSame(boards, container.boardRepository)
    }

    @Test
    fun entregaElRepositorioDeColumnasInyectado() {
        val columns = FakeColumnRepository()

        val container = container(columnRepository = columns)

        assertSame(columns, container.columnRepository)
    }

    @Test
    fun entregaElRepositorioDeTareasInyectado() {
        val tasks = FakeTaskRepository()

        val container = container(taskRepository = tasks)

        assertSame(tasks, container.taskRepository)
    }

    @Test
    fun container_exponeSubtaskRepository() {
        val subtasks = FakeSubtaskRepository()

        val container = container(subtaskRepository = subtasks)

        assertSame(subtasks, container.subtaskRepository)
    }

    @Test
    fun container_exponeMyDayRepository() {
        val myDay = FakeMyDayRepository()

        val container = container(myDayRepository = myDay)

        assertSame(myDay, container.myDayRepository)
    }

    @Test
    fun conversorUsaLaZonaEntregada() {
        val container = container()

        val local = container.dateTimeConverter.toLocalDateTime(Instant.parse("2026-10-12T23:30:00Z"))

        assertEquals(LocalDateTime.of(2026, 10, 13, 12, 30), local)
    }

    @Test
    fun entregaElReminderReconcilerRecibido() {
        val reconciler = reconciler(FakePreferencesRepository(), FakeTaskRepository(), FakeReminderRepository())

        assertSame(reconciler, container(reminderReconciler = reconciler).reminderReconciler)
    }

    @Test
    fun entregaElReminderRepositoryRecibido() {
        val reminders = FakeReminderRepository()

        assertSame(reminders, container(reminderRepository = reminders).reminderRepository)
    }

    private fun container(
        preferencesRepository: PreferencesRepository = FakePreferencesRepository(),
        boardRepository: BoardRepository = FakeBoardRepository(),
        columnRepository: ColumnRepository = FakeColumnRepository(),
        taskRepository: TaskRepository = FakeTaskRepository(),
        subtaskRepository: SubtaskRepository = FakeSubtaskRepository(),
        myDayRepository: MyDayRepository = FakeMyDayRepository(),
        reminderRepository: ReminderRepository = FakeReminderRepository(),
        reminderReconciler: ReminderReconciler =
            reconciler(preferencesRepository, taskRepository, reminderRepository),
    ): AppContainer = AppContainer(
        preferencesRepository = preferencesRepository,
        timeZoneProvider = TimeZoneProvider { auckland },
        boardRepository = boardRepository,
        columnRepository = columnRepository,
        taskRepository = taskRepository,
        subtaskRepository = subtaskRepository,
        myDayRepository = myDayRepository,
        reminderRepository = reminderRepository,
        reminderReconciler = reminderReconciler,
    )

    private fun reconciler(
        preferences: PreferencesRepository,
        tasks: TaskRepository,
        reminders: ReminderRepository,
    ) = ReminderReconciler(
        tasks = tasks,
        reminders = reminders,
        preferences = preferences,
        checkpoints = InMemoryCheckpointStore(),
        scheduler = RecordingReminderScheduler(),
        notifier = RecordingNotifier(),
        timeZone = TimeZoneProvider { ZoneId.of("UTC") },
        clock = { Instant.EPOCH },
    )

    private class FakeBoardRepository : BoardRepository {
        override fun observeBoards(): Flow<List<Board>> = MutableStateFlow(emptyList())

        override suspend fun createBoard(name: String): DomainResult<Board> = unused()

        override suspend fun renameBoard(id: String, name: String): DomainResult<Board> = unused()

        override suspend fun deleteBoard(id: String): DomainResult<Unit> = unused()

        private fun unused(): DomainResult.Failure =
            DomainResult.Failure(DomainError.Unknown("UNUSED", "doble de test"))
    }

    private class FakeColumnRepository : ColumnRepository {
        override fun observeColumns(boardId: String): Flow<List<Column>> = MutableStateFlow(emptyList())

        override suspend fun createColumn(boardId: String, name: String): DomainResult<Column> = unused()

        override suspend fun renameColumn(id: String, name: String): DomainResult<Column> = unused()

        override suspend fun setWipLimit(columnId: String, limit: Int?): DomainResult<Column> = unused()

        override suspend fun moveColumn(id: String, toIndex: Int): DomainResult<Column> = unused()

        override suspend fun deleteColumn(id: String): DomainResult<Unit> = unused()

        private fun unused(): DomainResult.Failure =
            DomainResult.Failure(DomainError.Unknown("UNUSED", "doble de test"))
    }

    private class FakeTaskRepository : TaskRepository {
        override fun observeTasks(boardId: String): Flow<List<Task>> = MutableStateFlow(emptyList())

        override fun observeReminderCandidates(): Flow<List<ReminderCandidate>> = MutableStateFlow(emptyList())

        override suspend fun reminderCandidates(): List<ReminderCandidate> = emptyList()

        override suspend fun activeTaskCount(): Int = 0

        override suspend fun getTask(id: String): DomainResult<Task> = unused()

        override suspend fun createTask(columnId: String, draft: TaskDraft): DomainResult<Task> = unused()

        override suspend fun updateTask(id: String, draft: TaskDraft): DomainResult<Task> = unused()

        override suspend fun moveTask(id: String, toColumnId: String, toIndex: Int): DomainResult<Task> = unused()

        override suspend fun deleteTask(id: String): DomainResult<Unit> = unused()

        private fun unused(): DomainResult.Failure =
            DomainResult.Failure(DomainError.Unknown("UNUSED", "doble de test"))
    }

    private class FakeMyDayRepository : MyDayRepository {
        override fun observeMyDay(): Flow<MyDay> = MutableStateFlow(MyDay(emptyList(), emptyList()))

        override fun observeMarkedTaskIds(): Flow<Set<String>> = MutableStateFlow(emptySet())

        override fun observeOverdueCount(): Flow<Int> = MutableStateFlow(0)

        override suspend fun setMarked(taskId: String, marked: Boolean): DomainResult<Unit> =
            DomainResult.Success(Unit)
    }

    private class FakeSubtaskRepository : SubtaskRepository {
        override fun observeSubtasks(taskId: String): Flow<List<Subtask>> = MutableStateFlow(emptyList())

        override fun observeSubtaskProgress(taskId: String): Flow<SubtaskProgress> =
            MutableStateFlow(SubtaskProgress(0, 0))

        override suspend fun createSubtask(taskId: String, draft: SubtaskDraft): DomainResult<Subtask> = unused()

        override suspend fun updateSubtask(id: String, draft: SubtaskDraft): DomainResult<Subtask> = unused()

        override suspend fun setSubtaskCompleted(id: String, completed: Boolean): DomainResult<Subtask> = unused()

        override suspend fun deleteSubtask(id: String): DomainResult<Unit> = unused()

        private fun unused(): DomainResult.Failure =
            DomainResult.Failure(DomainError.Unknown("UNUSED", "doble de test"))
    }
}
