package com.simposio.taskeer.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.SubtaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.SubtaskDraft
import java.time.LocalDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SubtasksViewModel(
    private val repository: SubtaskRepository,
    private val dateTimeConverter: DateTimeConverter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubtasksUiState())

    val uiState: StateFlow<SubtasksUiState> = _uiState.asStateFlow()

    private var collectJob: Job? = null

    fun open(taskId: String) {
        collectJob?.cancel()
        _uiState.value = SubtasksUiState(taskId = taskId)
        collectJob = viewModelScope.launch {
            repository.observeSubtasks(taskId).collect { items ->
                _uiState.update { it.copy(items = items) }
            }
        }
    }

    fun close() {
        collectJob?.cancel()
        collectJob = null
        _uiState.value = SubtasksUiState()
    }

    fun onNewTitleChange(value: String) {
        _uiState.update { it.copy(newTitle = value, error = null) }
    }

    fun add() {
        val state = _uiState.value
        val taskId = state.taskId ?: return
        if (state.newTitle.isBlank()) {
            return
        }
        viewModelScope.launch {
            when (val result = repository.createSubtask(taskId, SubtaskDraft(state.newTitle))) {
                is DomainResult.Success -> _uiState.update { it.copy(newTitle = "", error = null) }
                is DomainResult.Failure -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    fun startEdit(id: String) {
        val subtask = _uiState.value.items.firstOrNull { it.id == id } ?: return
        _uiState.update {
            it.copy(
                editingId = id,
                editingTitle = subtask.title,
                editingDueAtLocal = subtask.dueAt?.let(dateTimeConverter::toLocalDateTime),
                editingAssigneeId = subtask.assigneeId,
                error = null,
            )
        }
    }

    fun onEditDueAtChange(value: LocalDateTime?) {
        _uiState.update { it.copy(editingDueAtLocal = value, error = null) }
    }

    fun onEditAssigneeChange(value: String?) {
        _uiState.update { it.copy(editingAssigneeId = value, error = null) }
    }

    fun onEditTitleChange(value: String) {
        _uiState.update { it.copy(editingTitle = value, error = null) }
    }

    fun saveEdit() {
        val state = _uiState.value
        val id = state.editingId ?: return
        val draft = SubtaskDraft(
            title = state.editingTitle,
            assigneeId = state.editingAssigneeId,
            dueAt = state.editingDueAtLocal?.let(dateTimeConverter::toInstant),
        )
        viewModelScope.launch {
            when (val result = repository.updateSubtask(id, draft)) {
                is DomainResult.Success -> cancelEdit()
                is DomainResult.Failure -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    fun cancelEdit() {
        _uiState.update {
            it.copy(
                editingId = null,
                editingTitle = "",
                editingDueAtLocal = null,
                editingAssigneeId = null,
                error = null,
            )
        }
    }

    fun toggle(id: String, completed: Boolean) {
        viewModelScope.launch {
            val result = repository.setSubtaskCompleted(id, completed)
            publishFailure(result)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            val result = repository.deleteSubtask(id)
            publishFailure(result)
        }
    }

    private fun publishFailure(result: DomainResult<*>) {
        if (result is DomainResult.Failure) {
            _uiState.update { it.copy(error = result.error) }
        }
    }
}
