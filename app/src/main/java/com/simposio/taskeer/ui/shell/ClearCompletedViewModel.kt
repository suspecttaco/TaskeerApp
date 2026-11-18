package com.simposio.taskeer.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class ClearCompletedUiState(
    val completedCount: Int = 0,
    val confirmCount: Int? = null,
    val error: DomainError? = null,
) {
    val clearEnabled: Boolean get() = completedCount > 0
}

class ClearCompletedViewModel(private val taskRepository: TaskRepository) : ViewModel() {

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<ClearCompletedUiState> = combine(
        taskRepository.observeCompletedCount(),
        local,
    ) { count, local ->
        ClearCompletedUiState(completedCount = count, confirmCount = local.confirmCount, error = local.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ClearCompletedUiState())

    fun requestClear() {
        val count = uiState.value.completedCount
        if (count > 0) local.value = Local(confirmCount = count)
    }

    fun confirm() {
        if (local.value.confirmCount == null) return
        viewModelScope.launch {
            when (val result = taskRepository.deleteCompleted()) {
                is DomainResult.Failure -> local.update { it.copy(error = result.error) }
                is DomainResult.Success -> local.value = Local()
            }
        }
    }

    fun cancel() {
        local.value = Local()
    }

    private data class Local(val confirmCount: Int? = null, val error: DomainError? = null)

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
