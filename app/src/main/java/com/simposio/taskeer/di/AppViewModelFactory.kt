package com.simposio.taskeer.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.simposio.taskeer.ui.app.AppViewModel
import com.simposio.taskeer.ui.boards.BoardsViewModel
import com.simposio.taskeer.ui.columns.ColumnsViewModel
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
            TaskEditorViewModel(container.taskRepository, container.dateTimeConverter) as T
        modelClass.isAssignableFrom(SubtasksViewModel::class.java) ->
            SubtasksViewModel(container.subtaskRepository) as T

        else -> error("AppViewModelFactory no sabe construir ${modelClass.name}")
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = when {
        modelClass.isAssignableFrom(ColumnsViewModel::class.java) ->
            ColumnsViewModel(container.columnRepository, extras.createSavedStateHandle()) as T

        modelClass.isAssignableFrom(TasksViewModel::class.java) ->
            TasksViewModel(container.taskRepository, extras.createSavedStateHandle()) as T

        else -> create(modelClass)
    }
}
