package com.simposio.taskeer.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.TaskListRepository
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

class MoveToListViewModel(
    private val taskRepository: TaskRepository,
    listRepository: TaskListRepository,
) : ViewModel() {

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<MoveToListUiState> = combine(listRepository.observeLists(), local) { lists, local ->
        MoveToListUiState(
            taskId = local.taskId,
            lists = lists.filter { it.list.id != local.currentListId },
            error = local.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MoveToListUiState())

    fun open(taskId: String, currentListId: String) {
        local.value = Local(taskId = taskId, currentListId = currentListId)
    }

    fun choose(listId: String) {
        val taskId = local.value.taskId ?: return
        viewModelScope.launch {
            when (val result = taskRepository.moveToList(taskId, listId)) {
                is DomainResult.Failure -> local.update { it.copy(error = result.error) }
                is DomainResult.Success -> local.value = Local()
            }
        }
    }

    fun dismiss() {
        local.value = Local()
    }

    private data class Local(
        val taskId: String? = null,
        val currentListId: String? = null,
        val error: DomainError? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
