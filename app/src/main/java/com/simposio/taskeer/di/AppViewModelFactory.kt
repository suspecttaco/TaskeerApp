package com.simposio.taskeer.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.simposio.taskeer.ui.app.AppViewModel
import com.simposio.taskeer.ui.board.BoardViewModel
import com.simposio.taskeer.ui.boards.BoardsViewModel
import com.simposio.taskeer.ui.columns.ColumnsViewModel
import com.simposio.taskeer.ui.inbox.InboxViewModel
import com.simposio.taskeer.ui.myday.MyDayViewModel
import com.simposio.taskeer.ui.myday.QuickAddViewModel
import com.simposio.taskeer.ui.notifications.NotificationsViewModel
import com.simposio.taskeer.ui.notifications.UnreadCounterViewModel
import com.simposio.taskeer.ui.permission.NotificationPermissionViewModel
import com.simposio.taskeer.ui.settings.SettingsViewModel
import com.simposio.taskeer.ui.tasks.AssignToBoardViewModel
import com.simposio.taskeer.ui.tasks.RemindersViewModel
import com.simposio.taskeer.ui.tasks.SubtasksViewModel
import com.simposio.taskeer.ui.tasks.TaskEditorViewModel
import com.simposio.taskeer.ui.tasks.TasksViewModel

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

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
            ) as T
        modelClass.isAssignableFrom(NotificationPermissionViewModel::class.java) ->
            NotificationPermissionViewModel(
                container.preferencesRepository,
                container.reminderRepository,
                container.notificationsEnabled,
                container.sdkInt,
            ) as T
        modelClass.isAssignableFrom(MyDayViewModel::class.java) ->
            MyDayViewModel(container.myDayRepository, container.dateTimeConverter) as T
        modelClass.isAssignableFrom(QuickAddViewModel::class.java) ->
            QuickAddViewModel(
                container.taskRepository,
                container.boardRepository,
                container.columnRepository,
            ) as T
        modelClass.isAssignableFrom(InboxViewModel::class.java) ->
            InboxViewModel(
                container.taskRepository,
                container.myDayRepository,
                container.dateTimeConverter,
            ) as T
        modelClass.isAssignableFrom(AssignToBoardViewModel::class.java) ->
            AssignToBoardViewModel(
                container.taskRepository,
                container.boardRepository,
                container.columnRepository,
            ) as T
        modelClass.isAssignableFrom(NotificationsViewModel::class.java) ->
            NotificationsViewModel(
                container.notificationHistoryRepository,
                container.taskRepository,
                container.dateTimeConverter,
            ) as T
        modelClass.isAssignableFrom(UnreadCounterViewModel::class.java) ->
            UnreadCounterViewModel(container.notificationHistoryRepository) as T

        else -> error("AppViewModelFactory no sabe construir ${modelClass.name}")
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = when {
        modelClass.isAssignableFrom(ColumnsViewModel::class.java) ->
            ColumnsViewModel(container.columnRepository, extras.createSavedStateHandle()) as T

        modelClass.isAssignableFrom(BoardViewModel::class.java) ->
            BoardViewModel(
                container.columnRepository,
                container.taskRepository,
                container.dateTimeConverter,
                extras.createSavedStateHandle(),
                container.myDayRepository,
                container.myDayRepository.observeMarkedTaskIds(),
            ) as T

        modelClass.isAssignableFrom(TasksViewModel::class.java) ->
            TasksViewModel(container.taskRepository, extras.createSavedStateHandle()) as T

        else -> create(modelClass)
    }
}
