package com.simposio.taskeer.ui.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.model.Task
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TasksViewModel(
    repository: TaskRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // El nombre del argumento debe coincidir con el de la ruta del tablero.
    val boardId: String =
        requireNotNull(savedStateHandle[BOARD_ID_ARG]) { "Falta el boardId de la ruta" }

    val tasks: StateFlow<List<Task>> = repository.observeTasks(boardId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    companion object {
        const val BOARD_ID_ARG: String = "boardId"

        private const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
