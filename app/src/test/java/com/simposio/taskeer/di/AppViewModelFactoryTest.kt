package com.simposio.taskeer.di

import com.simposio.taskeer.ui.auth.SessionViewModel
import com.simposio.taskeer.data.reminder.ReminderReconciler
import com.simposio.taskeer.data.repository.BoardRepository
import com.simposio.taskeer.data.repository.ColumnRepository
import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.data.repository.PlannedRepository
import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.data.repository.ReminderRepository
import com.simposio.taskeer.data.repository.SubtaskRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.testing.FakeAuthRepository
import com.simposio.taskeer.testing.FakeCalendarRepository
import com.simposio.taskeer.testing.FakeDayWindowSource
import com.simposio.taskeer.testing.FakeDestinationTaskRepository
import com.simposio.taskeer.testing.FakeNotificationHistoryRepository
import com.simposio.taskeer.testing.FakeNotificationInboxRepository
import com.simposio.taskeer.testing.FakeNotificationLiveChannel
import com.simposio.taskeer.testing.FakeSearchRepository
import com.simposio.taskeer.testing.FakeWorkspaceRepository
import com.simposio.taskeer.testing.FakePreferencesRepository
import com.simposio.taskeer.testing.FakeTaskListRepository
import com.simposio.taskeer.testing.InMemoryCheckpointStore
import com.simposio.taskeer.testing.MainSources
import com.simposio.taskeer.testing.RecordingNotifier
import com.simposio.taskeer.testing.RecordingReminderScheduler
import com.simposio.taskeer.ui.alltasks.AllTasksViewModel
import com.simposio.taskeer.ui.calendar.CalendarViewModel
import com.simposio.taskeer.ui.calendar.EventEditorViewModel
import com.simposio.taskeer.ui.lists.TaskListsViewModel
import com.simposio.taskeer.ui.workspaces.WorkspacesViewModel
import com.simposio.taskeer.ui.myday.MySuggestionsViewModel
import com.simposio.taskeer.ui.quickadd.QuickAddViewModel
import com.simposio.taskeer.ui.shell.ClearCompletedViewModel
import com.simposio.taskeer.ui.tasks.TaskDeletionViewModel
import com.simposio.taskeer.ui.tasks.TaskEditorViewModel
import com.simposio.taskeer.ui.tasks.MoveToListViewModel
import com.simposio.taskeer.ui.upcoming.UpcomingViewModel
import com.simposio.taskeer.ui.accountsecurity.ChangePasswordViewModel
import com.simposio.taskeer.ui.passwordreset.ResetPasswordViewModel
import com.simposio.taskeer.ui.verifyemail.ResendVerificationViewModel
import com.simposio.taskeer.ui.verifyemail.VerifyEmailViewModel
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun crea_quickAddConElRepositorioDeListas() {
        val factory = AppViewModelFactory(container())

        assertEquals(QuickAddViewModel::class.java, factory.create(QuickAddViewModel::class.java).javaClass)
    }

    @Test
    fun crea_vistasPorFechaYLimpiar() {
        val factory = AppViewModelFactory(container())

        assertEquals(UpcomingViewModel::class.java, factory.create(UpcomingViewModel::class.java).javaClass)
        assertEquals(AllTasksViewModel::class.java, factory.create(AllTasksViewModel::class.java).javaClass)
        assertEquals(ClearCompletedViewModel::class.java, factory.create(ClearCompletedViewModel::class.java).javaClass)
    }

    @Test
    fun crea_sugerenciasDeMiDia() {
        val factory = AppViewModelFactory(container())

        assertEquals(MySuggestionsViewModel::class.java, factory.create(MySuggestionsViewModel::class.java).javaClass)
    }

    @Test
    fun crea_borradoConDeshacer() {
        val factory = AppViewModelFactory(container())

        assertEquals(TaskDeletionViewModel::class.java, factory.create(TaskDeletionViewModel::class.java).javaClass)
    }

    @Test
    fun crea_editorDeTareaConLosRepositoriosDeContexto() {
        val factory = AppViewModelFactory(container())

        assertEquals(TaskEditorViewModel::class.java, factory.create(TaskEditorViewModel::class.java).javaClass)
    }

    @Test
    fun crea_calendarioYEditorDeEventos() {
        val factory = AppViewModelFactory(container(FakePreferencesRepository()))

        assertEquals(CalendarViewModel::class.java, factory.create(CalendarViewModel::class.java).javaClass)
        assertEquals(EventEditorViewModel::class.java, factory.create(EventEditorViewModel::class.java).javaClass)
    }

    @Test
    fun creaWorkspacesViewModel() {
        val factory = AppViewModelFactory(container())

        assertEquals(WorkspacesViewModel::class.java, factory.create(WorkspacesViewModel::class.java).javaClass)
    }

    private fun container(
        preferences: PreferencesRepository = stub<PreferencesRepository>(),
    ): AppContainer = AppContainer(
        preferencesRepository = preferences,
        timeZoneProvider = { java.time.ZoneOffset.UTC },
        boardRepository = flowStub<BoardRepository>(),
        columnRepository = flowStub<ColumnRepository>(),
        taskRepository = FakeDestinationTaskRepository(),
        taskListRepository = lists,
        subtaskRepository = stub<SubtaskRepository>(),
        myDayRepository = flowStub<MyDayRepository>(),
        plannedRepository = flowStub<PlannedRepository>(),
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
        calendarRepository = FakeCalendarRepository(),
        dayWindowSource = FakeDayWindowSource(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC),
        searchRepository = FakeSearchRepository(),
        authRepository = FakeAuthRepository(),
        notificationInboxRepository = FakeNotificationInboxRepository(),
        notificationLiveChannel = FakeNotificationLiveChannel(),
        workspaceRepository = FakeWorkspaceRepository(),
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

    @Test
    fun creaSessionViewModel() {
        val factory = AppViewModelFactory(container())

        assertEquals(SessionViewModel::class.java, factory.create(SessionViewModel::class.java).javaClass)
    }

    // `createSavedStateHandle` exige un SavedStateRegistryOwner real: se comprueba el cableado por fuente.
    @Test
    fun creaRegisterViewModel() {
        val factory = MainSources.kotlin("di/AppViewModelFactory.kt")

        assertTrue(
            factory.contains("RegisterViewModel(container.authRepository, extras.createSavedStateHandle())"),
        )
    }

    @Test
    fun creaLoginViewModel() {
        val factory = MainSources.kotlin("di/AppViewModelFactory.kt")

        val call = factory.substringAfter("LoginViewModel(").substringBefore(") as T").filterNot { it.isWhitespace() }

        assertEquals(
            "container.authRepository,container.googleCredentialSource,container.googleSignInConfig,extras.createSavedStateHandle()",
            call.trimEnd(','),
        )
    }

    @Test
    fun creaVerifyEmailYResendViewModels() {
        val factory = AppViewModelFactory(container())

        assertEquals(VerifyEmailViewModel::class.java, factory.create(VerifyEmailViewModel::class.java).javaClass)
        assertEquals(
            ResendVerificationViewModel::class.java,
            factory.create(ResendVerificationViewModel::class.java).javaClass,
        )
    }

    @Test
    fun creaResetPasswordViewModel() {
        val factory = AppViewModelFactory(container())

        assertEquals(ResetPasswordViewModel::class.java, factory.create(ResetPasswordViewModel::class.java).javaClass)
    }

    @Test
    fun creaChangePasswordYRegenerateCodesViewModels() {
        val factory = AppViewModelFactory(container())
        val source = MainSources.kotlin("di/AppViewModelFactory.kt")

        assertEquals(ChangePasswordViewModel::class.java, factory.create(ChangePasswordViewModel::class.java).javaClass)
        assertTrue(source.contains("RegenerateCodesViewModel(container.authRepository, extras.createSavedStateHandle())"))
    }
}
