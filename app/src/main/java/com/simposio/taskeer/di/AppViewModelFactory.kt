package com.simposio.taskeer.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.simposio.taskeer.ui.accountsecurity.ChangePasswordViewModel
import com.simposio.taskeer.ui.accountsecurity.RegenerateCodesViewModel
import com.simposio.taskeer.ui.app.AppViewModel
import com.simposio.taskeer.ui.auth.LoginViewModel
import com.simposio.taskeer.ui.auth.SessionViewModel
import com.simposio.taskeer.ui.sync.SyncViewModel
import com.simposio.taskeer.ui.components.settleMillis
import com.simposio.taskeer.ui.board.BoardViewModel
import com.simposio.taskeer.ui.calendar.CalendarViewModel
import com.simposio.taskeer.ui.calendar.EventEditorViewModel
import com.simposio.taskeer.ui.boards.BoardsViewModel
import com.simposio.taskeer.ui.columns.ColumnsViewModel
import com.simposio.taskeer.ui.alltasks.AllTasksViewModel
import com.simposio.taskeer.ui.lists.ListViewModel
import com.simposio.taskeer.ui.lists.TaskListsViewModel
import com.simposio.taskeer.ui.myday.MyDayViewModel
import com.simposio.taskeer.ui.myday.MySuggestionsViewModel
import com.simposio.taskeer.ui.notifications.AccountNotificationsViewModel
import com.simposio.taskeer.ui.notifications.NotificationsViewModel
import com.simposio.taskeer.ui.notifications.UnreadCounterViewModel
import com.simposio.taskeer.ui.passwordreset.ResetPasswordViewModel
import com.simposio.taskeer.ui.permission.NotificationPermissionViewModel
import com.simposio.taskeer.ui.quickadd.QuickAddViewModel
import com.simposio.taskeer.ui.register.RegisterViewModel
import com.simposio.taskeer.ui.search.SearchViewModel
import com.simposio.taskeer.ui.settings.SettingsViewModel
import com.simposio.taskeer.ui.shell.ClearCompletedViewModel
import com.simposio.taskeer.ui.tasks.AssignToBoardViewModel
import com.simposio.taskeer.ui.tasks.MoveToListViewModel
import com.simposio.taskeer.ui.tasks.RemindersViewModel
import com.simposio.taskeer.ui.tasks.SubtasksViewModel
import com.simposio.taskeer.ui.tasks.TaskDeletionViewModel
import com.simposio.taskeer.ui.tasks.TaskEditorViewModel
import com.simposio.taskeer.ui.tasks.TaskRescheduler
import com.simposio.taskeer.ui.tasks.TasksViewModel
import com.simposio.taskeer.ui.upcoming.UpcomingViewModel
import com.simposio.taskeer.ui.verifyemail.ResendVerificationViewModel
import com.simposio.taskeer.ui.verifyemail.VerifyEmailViewModel
import com.simposio.taskeer.ui.workspaces.BoardAccessViewModel
import com.simposio.taskeer.ui.workspaces.MyInvitationsViewModel
import com.simposio.taskeer.ui.workspaces.ServerBoardsViewModel
import com.simposio.taskeer.ui.workspaces.WorkspaceInvitationsViewModel
import com.simposio.taskeer.ui.workspaces.WorkspaceMembersViewModel
import com.simposio.taskeer.ui.workspaces.WorkspacesViewModel
import java.time.Instant

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    private fun currentSettleMillis(): Long = settleMillis(container.animationScale())

    private fun rescheduler() = TaskRescheduler(container.taskRepository, container.dateTimeConverter)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(AppViewModel::class.java) ->
            AppViewModel(container.preferencesRepository) as T
        modelClass.isAssignableFrom(BoardsViewModel::class.java) ->
            BoardsViewModel(container.boardRepository) as T
        modelClass.isAssignableFrom(TaskEditorViewModel::class.java) ->
            TaskEditorViewModel(
                container.taskRepository,
                container.dateTimeConverter,
                container.preferencesRepository,
                container.timeZoneProvider,
                container.taskListRepository,
                container.myDayRepository,
                container.boardRepository,
                container.columnRepository,
            ) as T
        modelClass.isAssignableFrom(SubtasksViewModel::class.java) ->
            SubtasksViewModel(container.subtaskRepository, container.dateTimeConverter) as T
        modelClass.isAssignableFrom(RemindersViewModel::class.java) ->
            RemindersViewModel(
                container.reminderRepository,
                container.preferencesRepository,
                container.timeZoneProvider,
                container.dateTimeConverter,
            ) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(
                container.preferencesRepository,
                container.reminderReconciler::onEnabled,
                SystemTimeZoneProvider::current,
            ) as T
        modelClass.isAssignableFrom(NotificationPermissionViewModel::class.java) ->
            NotificationPermissionViewModel(
                container.preferencesRepository,
                container.reminderRepository,
                container.notificationsEnabled,
                container.sdkInt,
            ) as T
        modelClass.isAssignableFrom(MyDayViewModel::class.java) ->
            MyDayViewModel(
                container.myDayRepository,
                container.taskRepository,
                container.dateTimeConverter,
                rescheduler(),
                settleMillis = ::currentSettleMillis,
            ) as T
        modelClass.isAssignableFrom(MySuggestionsViewModel::class.java) ->
            MySuggestionsViewModel(
                container.myDayRepository,
                container.preferencesRepository,
                Instant::now,
                container.timeZoneProvider,
                container.dateTimeConverter,
            ) as T
        modelClass.isAssignableFrom(UpcomingViewModel::class.java) ->
            UpcomingViewModel(
                container.plannedRepository,
                container.taskRepository,
                container.dateTimeConverter,
                rescheduler(),
                settleMillis = ::currentSettleMillis,
            ) as T
        modelClass.isAssignableFrom(AllTasksViewModel::class.java) ->
            AllTasksViewModel(
                container.plannedRepository,
                container.taskRepository,
                container.dateTimeConverter,
                rescheduler(),
                settleMillis = ::currentSettleMillis,
            ) as T
        modelClass.isAssignableFrom(ClearCompletedViewModel::class.java) ->
            ClearCompletedViewModel(container.taskRepository) as T
        modelClass.isAssignableFrom(TaskDeletionViewModel::class.java) ->
            TaskDeletionViewModel(container.taskRepository) as T
        modelClass.isAssignableFrom(QuickAddViewModel::class.java) ->
            QuickAddViewModel(
                container.taskRepository,
                container.taskListRepository,
                container.dateTimeConverter,
            ) as T
        modelClass.isAssignableFrom(TaskListsViewModel::class.java) ->
            TaskListsViewModel(container.taskListRepository) as T
        modelClass.isAssignableFrom(MoveToListViewModel::class.java) ->
            MoveToListViewModel(container.taskRepository, container.taskListRepository) as T
        modelClass.isAssignableFrom(AssignToBoardViewModel::class.java) ->
            AssignToBoardViewModel(
                container.taskRepository,
                container.boardRepository,
                container.columnRepository,
            ) as T
        modelClass.isAssignableFrom(CalendarViewModel::class.java) ->
            CalendarViewModel(
                container.calendarRepository,
                container.plannedRepository,
                container.preferencesRepository,
                container.dayWindowSource,
                container.timeZoneProvider,
                container.firstDayOfWeek,
                container.calendarReadGranted,
                container.calendarWriteGranted,
            ) as T
        modelClass.isAssignableFrom(EventEditorViewModel::class.java) ->
            EventEditorViewModel(
                container.calendarRepository,
                container.preferencesRepository,
                container.calendarReadGranted,
                container.calendarWriteGranted,
                container.timeZoneProvider,
            ) as T
        modelClass.isAssignableFrom(NotificationsViewModel::class.java) ->
            NotificationsViewModel(
                container.notificationHistoryRepository,
                container.taskRepository,
                container.dateTimeConverter,
                container.reminderRepository,
                container.preferencesRepository,
                container.timeZoneProvider,
            ) as T
        modelClass.isAssignableFrom(UnreadCounterViewModel::class.java) ->
            UnreadCounterViewModel(
                container.notificationHistoryRepository,
                container.notificationInboxRepository,
                container.notificationLiveChannel,
            ) as T
        modelClass.isAssignableFrom(WorkspacesViewModel::class.java) ->
            WorkspacesViewModel(container.workspaceRepository, container.authRepository.sessionState) as T
        modelClass.isAssignableFrom(MyInvitationsViewModel::class.java) ->
            MyInvitationsViewModel(
                container.myInvitationsRepository,
                container.workspaceRepository,
                container.authRepository.sessionState,
            ) as T
        modelClass.isAssignableFrom(AccountNotificationsViewModel::class.java) ->
            AccountNotificationsViewModel(
                container.notificationInboxRepository,
                container.authRepository.sessionState,
                container.taskRepository,
                container.dateTimeConverter,
            ) as T

        modelClass.isAssignableFrom(SyncViewModel::class.java) ->
            SyncViewModel(container.syncRepository) as T

        modelClass.isAssignableFrom(SessionViewModel::class.java) ->
            SessionViewModel(container.authRepository) as T

        modelClass.isAssignableFrom(VerifyEmailViewModel::class.java) ->
            VerifyEmailViewModel(container.authRepository) as T
        modelClass.isAssignableFrom(ResendVerificationViewModel::class.java) ->
            ResendVerificationViewModel(container.authRepository) as T

        modelClass.isAssignableFrom(ResetPasswordViewModel::class.java) ->
            ResetPasswordViewModel(container.authRepository) as T
        modelClass.isAssignableFrom(ChangePasswordViewModel::class.java) ->
            ChangePasswordViewModel(container.authRepository) as T

        else -> error("AppViewModelFactory no sabe construir ${modelClass.name}")
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = when {
        modelClass.isAssignableFrom(ColumnsViewModel::class.java) ->
            ColumnsViewModel(container.columnRepository, extras.createSavedStateHandle()) as T

        modelClass.isAssignableFrom(BoardViewModel::class.java) ->
            BoardViewModel(
                container.boardRepository,
                container.columnRepository,
                container.taskRepository,
                container.dateTimeConverter,
                extras.createSavedStateHandle(),
                container.myDayRepository,
                container.myDayRepository.observeMarkedTaskIds(),
            ) as T

        modelClass.isAssignableFrom(ListViewModel::class.java) ->
            ListViewModel(
                container.taskRepository,
                container.taskListRepository,
                container.myDayRepository,
                container.dateTimeConverter,
                extras.createSavedStateHandle(),
                rescheduler(),
                settleMillis = ::currentSettleMillis,
            ) as T

        modelClass.isAssignableFrom(LoginViewModel::class.java) ->
            LoginViewModel(
                container.authRepository,
                container.googleCredentialSource,
                container.googleSignInConfig,
                extras.createSavedStateHandle(),
            ) as T
        modelClass.isAssignableFrom(RegisterViewModel::class.java) ->
            RegisterViewModel(container.authRepository, extras.createSavedStateHandle()) as T
        modelClass.isAssignableFrom(RegenerateCodesViewModel::class.java) ->
            RegenerateCodesViewModel(container.authRepository, extras.createSavedStateHandle()) as T
        modelClass.isAssignableFrom(SearchViewModel::class.java) ->
            SearchViewModel(container.searchRepository, extras.createSavedStateHandle()) as T

        modelClass.isAssignableFrom(TasksViewModel::class.java) ->
            TasksViewModel(container.taskRepository, extras.createSavedStateHandle()) as T

        modelClass.isAssignableFrom(WorkspaceMembersViewModel::class.java) ->
            WorkspaceMembersViewModel(
                extras.createSavedStateHandle(),
                container.workspaceMembersRepository,
                container.workspaceRepository,
                container.authRepository.sessionState,
            ) as T

        modelClass.isAssignableFrom(ServerBoardsViewModel::class.java) ->
            ServerBoardsViewModel(
                extras.createSavedStateHandle(),
                container.serverBoardsRepository,
                container.workspaceRepository,
                container.authRepository.sessionState,
            ) as T

        modelClass.isAssignableFrom(BoardAccessViewModel::class.java) ->
            BoardAccessViewModel(
                extras.createSavedStateHandle(),
                container.boardAccessRepository,
                container.workspaceMembersRepository,
                container.workspaceRepository,
                container.roleChangeSignals,
                container.authRepository.sessionState,
            ) as T

        modelClass.isAssignableFrom(WorkspaceInvitationsViewModel::class.java) ->
            WorkspaceInvitationsViewModel(
                extras.createSavedStateHandle(),
                container.workspaceInvitationsRepository,
                container.workspaceMembersRepository,
                container.workspaceRepository,
                container.authRepository.sessionState,
            ) as T

        else -> create(modelClass)
    }
}
