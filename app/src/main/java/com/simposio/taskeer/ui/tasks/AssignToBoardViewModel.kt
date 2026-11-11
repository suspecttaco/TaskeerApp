package com.simposio.taskeer.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.BoardRepository
import com.simposio.taskeer.data.repository.ColumnRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AssignToBoardViewModel(
    private val taskRepository: TaskRepository,
    boardRepository: BoardRepository,
    columnRepository: ColumnRepository,
) : ViewModel() {

    private val local = MutableStateFlow(Local())

    // Asignar a una columna de cierre equivale a darla por hecha y la regla de negocio lo permite.
    val uiState: StateFlow<AssignToBoardUiState> = combine(
        observeBoardDestinations(boardRepository, columnRepository, taskRepository, includeDoneColumns = true),
        local,
    ) { destinations, local ->
        AssignToBoardUiState(taskId = local.taskId, destinations = destinations, error = local.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AssignToBoardUiState())

    fun open(taskId: String) {
        local.value = Local(taskId = taskId)
    }

    fun choose(columnId: String) {
        val taskId = local.value.taskId ?: return
        viewModelScope.launch {
            when (val result = taskRepository.assignToColumn(taskId, columnId)) {
                is DomainResult.Failure -> local.update { it.copy(error = result.error) }
                is DomainResult.Success -> local.value = Local()
            }
        }
    }

    fun dismiss() {
        local.value = Local()
    }

    private data class Local(val taskId: String? = null, val error: DomainError? = null)

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
