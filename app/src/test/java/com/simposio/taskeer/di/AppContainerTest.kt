package com.simposio.taskeer.di

import com.simposio.taskeer.data.reminder.ReminderReconciler
import com.simposio.taskeer.data.repository.BoardRepository
import com.simposio.taskeer.data.repository.CalendarRepository
import com.simposio.taskeer.data.repository.ColumnRepository
import com.simposio.taskeer.data.repository.DayWindowSource
import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.data.repository.PlannedRepository
import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.data.repository.ReminderRepository
import com.simposio.taskeer.data.repository.SubtaskRepository
import com.simposio.taskeer.data.repository.TaskListRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.calendar.TaskDue
import com.simposio.taskeer.domain.date.FirstDayOfWeekProvider
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.MyDay
import com.simposio.taskeer.domain.model.MyDayTask
import com.simposio.taskeer.domain.model.Subtask
import com.simposio.taskeer.domain.model.SubtaskDraft
import com.simposio.taskeer.domain.model.SubtaskProgress
import com.simposio.taskeer.domain.model.ReminderCandidate
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskCreation
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.planned.AllTasksView
import com.simposio.taskeer.domain.planned.UpcomingDay
import com.simposio.taskeer.testing.FakeAuthRepository
import com.simposio.taskeer.testing.FakeCalendarRepository
import com.simposio.taskeer.testing.FakeDayWindowSource
import com.simposio.taskeer.testing.FakePreferencesRepository
import com.simposio.taskeer.testing.FakeReminderRepository
import com.simposio.taskeer.testing.FakeMyInvitationsRepository
import com.simposio.taskeer.testing.FakeNotificationHistoryRepository
import com.simposio.taskeer.testing.FakeNotificationInboxRepository
import com.simposio.taskeer.testing.FakeNotificationLiveChannel
import com.simposio.taskeer.testing.FakeSearchRepository
import com.simposio.taskeer.testing.FakeWorkspaceInvitationsRepository
import com.simposio.taskeer.testing.FakeWorkspaceMembersRepository
import com.simposio.taskeer.testing.FakeWorkspaceRepository
import com.simposio.taskeer.testing.FakeTaskListRepository
import com.simposio.taskeer.testing.InMemoryCheckpointStore
import com.simposio.taskeer.testing.RecordingNotifier
import com.simposio.taskeer.testing.RecordingReminderScheduler
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
    fun exponeLaEscalaDeAnimacionInyectable() {
        val container = container(animationScale = { 0f })

        assertEquals(0f, container.animationScale(), 0f)
        assertEquals(1f, container().animationScale(), 0f)
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
    fun taskListRepository_noEsNulo() {
        val lists = FakeTaskListRepository()

        val container = container(taskListRepository = lists)

        assertSame(lists, container.taskListRepository)
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
    fun container_exponePlannedRepository() {
        val planned = FakePlannedRepository()

        val container = container(plannedRepository = planned)

        assertSame(planned, container.plannedRepository)
    }

    @Test
    fun container_exponeElCalendarioYSusCosturas() {
        val calendar = FakeCalendarRepository()
        val windows = FakeDayWindowSource(Instant.EPOCH, ZoneOffset.UTC)

        val container = container(
            calendarRepository = calendar,
            dayWindowSource = windows,
            calendarReadGranted = { true },
        )

        assertSame(calendar, container.calendarRepository)
        assertSame(windows, container.dayWindowSource)
        assertEquals(DayOfWeek.MONDAY, container.firstDayOfWeek.current())
        assertEquals(true, container.calendarReadGranted())
        assertEquals(false, container.calendarWriteGranted())
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
        taskListRepository: TaskListRepository = FakeTaskListRepository(),
        subtaskRepository: SubtaskRepository = FakeSubtaskRepository(),
        myDayRepository: MyDayRepository = FakeMyDayRepository(),
        plannedRepository: PlannedRepository = FakePlannedRepository(),
        reminderRepository: ReminderRepository = FakeReminderRepository(),
        reminderReconciler: ReminderReconciler =
            reconciler(preferencesRepository, taskRepository, reminderRepository),
        animationScale: () -> Float = { 1f },
        calendarRepository: CalendarRepository = FakeCalendarRepository(),
        dayWindowSource: DayWindowSource = FakeDayWindowSource(Instant.EPOCH, ZoneOffset.UTC),
        firstDayOfWeek: FirstDayOfWeekProvider = FirstDayOfWeekProvider { DayOfWeek.MONDAY },
        calendarReadGranted: () -> Boolean = { false },
        calendarWriteGranted: () -> Boolean = { false },
    ): AppContainer = AppContainer(
        preferencesRepository = preferencesRepository,
        timeZoneProvider = TimeZoneProvider { auckland },
        boardRepository = boardRepository,
        columnRepository = columnRepository,
        taskRepository = taskRepository,
        taskListRepository = taskListRepository,
        subtaskRepository = subtaskRepository,
        myDayRepository = myDayRepository,
        plannedRepository = plannedRepository,
        reminderRepository = reminderRepository,
        reminderReconciler = reminderReconciler,
        notificationHistoryRepository = FakeNotificationHistoryRepository(),
        calendarRepository = calendarRepository,
        dayWindowSource = dayWindowSource,
        searchRepository = FakeSearchRepository(),
        authRepository = FakeAuthRepository(),
        notificationInboxRepository = FakeNotificationInboxRepository(),
        notificationLiveChannel = FakeNotificationLiveChannel(),
        workspaceRepository = FakeWorkspaceRepository(),
        workspaceMembersRepository = FakeWorkspaceMembersRepository(),
        workspaceInvitationsRepository = FakeWorkspaceInvitationsRepository(),
        myInvitationsRepository = FakeMyInvitationsRepository(),
        firstDayOfWeek = firstDayOfWeek,
        calendarReadGranted = calendarReadGranted,
        calendarWriteGranted = calendarWriteGranted,
        animationScale = animationScale,
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
        override fun observeAllColumns(): Flow<List<Column>> = emptyFlow()

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
        override fun observeList(listId: String): Flow<List<Task>> = emptyFlow()

        override fun observeActiveCountsByColumn(): Flow<Map<String, Int>> = emptyFlow()

        override suspend fun assignToColumn(id: String, columnId: String): DomainResult<Task> =
            error("no se usa en este test")

        override fun observeTasks(boardId: String): Flow<List<Task>> = MutableStateFlow(emptyList())

        override fun observeReminderCandidates(): Flow<List<ReminderCandidate>> = MutableStateFlow(emptyList())

        override suspend fun reminderCandidates(): List<ReminderCandidate> = emptyList()

        override suspend fun activeTaskCount(): Int = 0

        override suspend fun getTask(id: String): DomainResult<Task> = unused()

        override suspend fun createTask(columnId: String?, draft: TaskDraft, listId: String?): DomainResult<Task> = unused()

        override suspend fun createTaskWithExtras(
            columnId: String?,
            creation: TaskCreation,
            listId: String?,
        ): DomainResult<Task> = unused()

        override suspend fun moveToList(id: String, listId: String): DomainResult<Task> = unused()

        override suspend fun updateTask(id: String, draft: TaskDraft): DomainResult<Task> = unused()

        override suspend fun moveTask(id: String, toColumnId: String, toIndex: Int): DomainResult<Task> = unused()

        override suspend fun setCompleted(id: String, completed: Boolean): DomainResult<Task> = unused()

        override fun observeCompletedCount(): Flow<Int> = error("no se usa")

        override suspend fun deleteCompleted(): DomainResult<List<String>> = error("no se usa")

        override suspend fun restoreTasks(ids: List<String>): DomainResult<Unit> = error("no se usa")

        override suspend fun deleteTask(id: String): DomainResult<Unit> = unused()

        private fun unused(): DomainResult.Failure =
            DomainResult.Failure(DomainError.Unknown("UNUSED", "doble de test"))
    }

    private class FakeMyDayRepository : MyDayRepository {
        override fun observeMyDay(): Flow<MyDay> = MutableStateFlow(MyDay(emptyList()))

        override fun observeMarkedTaskIds(): Flow<Set<String>> = MutableStateFlow(emptySet())

        override fun observeOverdueCount(): Flow<Int> = MutableStateFlow(0)

        override fun observeSuggestions(): Flow<List<MyDayTask>> = emptyFlow()

        override fun observeSuggestionCount(): Flow<Int> = emptyFlow()

        override suspend fun dismissSuggestion(taskId: String): DomainResult<Unit> = DomainResult.Success(Unit)

        override suspend fun setMarked(taskId: String, marked: Boolean): DomainResult<Unit> =
            DomainResult.Success(Unit)
    }

    private class FakePlannedRepository : PlannedRepository {
        override fun observeUpcoming(): Flow<List<UpcomingDay>> = emptyFlow()

        override fun observeAllTasks(): Flow<AllTasksView> = emptyFlow()

        override fun observeDatedTasks(): Flow<List<TaskDue>> = emptyFlow()
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

    @Test
    fun exponeAuthRepository() {
        val auth = FakeAuthRepository()
        val container = AppContainer(
            preferencesRepository = FakePreferencesRepository(),
            timeZoneProvider = TimeZoneProvider { auckland },
            boardRepository = FakeBoardRepository(),
            columnRepository = FakeColumnRepository(),
            taskRepository = FakeTaskRepository(),
            taskListRepository = FakeTaskListRepository(),
            subtaskRepository = FakeSubtaskRepository(),
            myDayRepository = FakeMyDayRepository(),
            plannedRepository = FakePlannedRepository(),
            reminderRepository = FakeReminderRepository(),
            reminderReconciler = reconciler(FakePreferencesRepository(), FakeTaskRepository(), FakeReminderRepository()),
            notificationHistoryRepository = FakeNotificationHistoryRepository(),
            calendarRepository = FakeCalendarRepository(),
            dayWindowSource = FakeDayWindowSource(Instant.EPOCH, ZoneOffset.UTC),
            searchRepository = FakeSearchRepository(),
            authRepository = auth,
            notificationInboxRepository = FakeNotificationInboxRepository(),
            notificationLiveChannel = FakeNotificationLiveChannel(),
            workspaceRepository = FakeWorkspaceRepository(),
            workspaceMembersRepository = FakeWorkspaceMembersRepository(),
            workspaceInvitationsRepository = FakeWorkspaceInvitationsRepository(),
            myInvitationsRepository = FakeMyInvitationsRepository(),
        )

        assertSame(auth, container.authRepository)
    }
}
