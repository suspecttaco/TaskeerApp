package com.simposio.taskeer.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.model.TaskPriority
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TaskEditorViewModel(
    private val repository: TaskRepository,
    private val dateTimeConverter: DateTimeConverter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskEditorUiState())

    val uiState: StateFlow<TaskEditorUiState> = _uiState.asStateFlow()

    fun openForCreate(columnId: String) {
        _uiState.value = TaskEditorUiState(isOpen = true, columnId = columnId)
    }

    fun openForEdit(taskId: String) {
        viewModelScope.launch {
            when (val result = repository.getTask(taskId)) {
                is DomainResult.Success -> _uiState.value = result.value.toEditorState()
                is DomainResult.Failure -> _uiState.value = TaskEditorUiState(
                    isOpen = true,
                    taskId = taskId,
                    error = result.error,
                )
            }
        }
    }

    fun onTitleChange(value: String) {
        _uiState.update { it.copy(title = value, error = null) }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value, error = null) }
    }

    fun onPriorityChange(priority: TaskPriority) {
        _uiState.update { it.copy(priority = priority, error = null) }
    }

    fun onDueAtChange(local: LocalDateTime?) {
        _uiState.update { it.copy(dueAtLocal = local, error = null) }
    }

    fun onAssigneeChange(assigneeId: String?) {
        _uiState.update { it.copy(assigneeId = assigneeId, error = null) }
    }

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank() || state.isSaving) {
            return
        }
        val draft = TaskDraft(
            title = state.title,
            description = state.description,
            priority = state.priority,
            // La conversión a UTC ocurre aquí: el repositorio solo conoce instantes.
            dueAt = state.dueAtLocal?.let(dateTimeConverter::toInstant),
            assigneeId = state.assigneeId,
        )
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, error = null)
            val result = if (state.taskId == null) {
                repository.createTask(state.columnId, draft)
            } else {
                repository.updateTask(state.taskId, draft)
            }
            when (result) {
                is DomainResult.Success -> dismiss()
                is DomainResult.Failure ->
                    _uiState.update { it.copy(isSaving = false, error = result.error) }
            }
        }
    }

    fun delete() {
        val taskId = _uiState.value.taskId ?: return
        viewModelScope.launch {
            when (val result = repository.deleteTask(taskId)) {
                is DomainResult.Success -> dismiss()
                is DomainResult.Failure -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    fun dismiss() {
        _uiState.value = TaskEditorUiState()
    }

    private fun Task.toEditorState(): TaskEditorUiState = TaskEditorUiState(
        isOpen = true,
        taskId = id,
        columnId = columnId,
        title = title,
        description = description.orEmpty(),
        priority = priority,
        dueAtLocal = dueAt?.let(dateTimeConverter::toLocalDateTime),
        assigneeId = assigneeId,
    )
}
