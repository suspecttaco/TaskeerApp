package com.simposio.taskeer.ui.myday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.BoardRepository
import com.simposio.taskeer.data.repository.ColumnRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.model.BoardDestination
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.TaskCreation
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.ui.tasks.observeBoardDestinations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class QuickAddViewModel(
    private val taskRepository: TaskRepository,
    boardRepository: BoardRepository,
    columnRepository: ColumnRepository,
) : ViewModel() {

    private val local = MutableStateFlow(Local())

    // Las columnas de cierre no se ofrecen: una tarea creada ahí ya estaría completada y no se vería en Mi día.
    val uiState: StateFlow<QuickAddUiState> = combine(
        observeBoardDestinations(boardRepository, columnRepository, taskRepository, includeDoneColumns = false),
        local,
    ) { destinations, local ->
        val label = (local.destination as? QuickAddDestination.InColumn)?.let { destinationLabel(destinations, it.columnId) }
        QuickAddUiState(
            title = local.title,
            destination = if (label == null) QuickAddDestination.Inbox else local.destination,
            destinationLabel = label,
            destinations = destinations,
            isPickerOpen = local.isPickerOpen,
            isSaving = local.isSaving,
            error = local.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), QuickAddUiState())

    fun onTitleChange(value: String) {
        local.update { it.copy(title = value) }
    }

    fun openPicker() {
        local.update { it.copy(isPickerOpen = true) }
    }

    fun closePicker() {
        local.update { it.copy(isPickerOpen = false) }
    }

    fun selectInbox() {
        local.update { it.copy(destination = QuickAddDestination.Inbox, isPickerOpen = false) }
    }

    fun selectColumn(columnId: String) {
        local.update { it.copy(destination = QuickAddDestination.InColumn(columnId), isPickerOpen = false) }
    }

    fun add() {
        val state = uiState.value
        if (state.title.isBlank() || state.isSaving) return
        val columnId = (state.destination as? QuickAddDestination.InColumn)?.columnId
        local.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val creation = TaskCreation(TaskDraft(title = state.title), addToMyDay = true)
            when (val result = taskRepository.createTaskWithExtras(columnId, creation)) {
                is DomainResult.Failure -> local.update { it.copy(isSaving = false, error = result.error) }
                is DomainResult.Success -> local.update { it.copy(isSaving = false, title = "", error = null) }
            }
        }
    }

    private fun destinationLabel(destinations: List<BoardDestination>, columnId: String): String? {
        destinations.forEach { board ->
            board.columns.firstOrNull { it.column.id == columnId }?.let { return "${board.board.name} › ${it.column.name}" }
        }
        return null
    }

    private data class Local(
        val title: String = "",
        val destination: QuickAddDestination = QuickAddDestination.Inbox,
        val isPickerOpen: Boolean = false,
        val isSaving: Boolean = false,
        val error: DomainError? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
