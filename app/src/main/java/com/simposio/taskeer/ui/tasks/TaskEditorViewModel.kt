package com.simposio.taskeer.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.model.SubtaskDraft
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskCreation
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.model.newClientId
import com.simposio.taskeer.domain.reminder.laterToday
import com.simposio.taskeer.domain.reminder.nextWeekAt
import com.simposio.taskeer.domain.reminder.tomorrowAt
import com.simposio.taskeer.domain.reminder.validateRemindAt
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TaskEditorViewModel(
    private val repository: TaskRepository,
    private val dateTimeConverter: DateTimeConverter,
    private val preferences: PreferencesRepository,
    private val timeZone: TimeZoneProvider,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskEditorUiState())

    val uiState: StateFlow<TaskEditorUiState> = _uiState.asStateFlow()

    fun openForCreate(columnId: String) {
        _uiState.value = TaskEditorUiState(isOpen = true, columnId = columnId)
    }

    fun openForEdit(taskId: String) {
        viewModelScope.launch {
            val result = repository.getTask(taskId)
            if (result is DomainResult.Success) {
                _uiState.value = result.value.toEditorState()
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

    fun onNewSubtaskTitleChange(value: String) {
        _uiState.update { it.copy(newSubtaskTitle = value) }
    }

    fun addSubtaskDraft() {
        _uiState.update { state ->
            val title = state.newSubtaskTitle.trim()
            if (title.isEmpty()) {
                state
            } else {
                state.copy(
                    subtaskDrafts = state.subtaskDrafts + SubtaskDraftItem(newClientId(), title),
                    newSubtaskTitle = "",
                    error = null,
                )
            }
        }
    }

    fun startEditSubtaskDraft(id: String) {
        _uiState.update { state ->
            val draft = state.subtaskDrafts.firstOrNull { it.id == id } ?: return@update state
            state.copy(
                editingDraftId = id,
                editingDraftTitle = draft.title,
                editingDraftDueAtLocal = draft.dueAtLocal,
                editingDraftAssigneeId = draft.assigneeId,
            )
        }
    }

    fun onEditSubtaskTitleChange(value: String) {
        _uiState.update { it.copy(editingDraftTitle = value) }
    }

    fun onEditSubtaskDueAtChange(local: LocalDateTime?) {
        _uiState.update { it.copy(editingDraftDueAtLocal = local) }
    }

    fun onEditSubtaskAssigneeChange(assigneeId: String?) {
        _uiState.update { it.copy(editingDraftAssigneeId = assigneeId) }
    }

    fun saveSubtaskDraftEdit() {
        _uiState.update { state ->
            val id = state.editingDraftId ?: return@update state
            val title = state.editingDraftTitle.trim()
            if (title.isEmpty()) {
                return@update state
            }
            state.copy(
                subtaskDrafts = state.subtaskDrafts.map {
                    if (it.id == id) {
                        it.copy(
                            title = title,
                            dueAtLocal = state.editingDraftDueAtLocal,
                            assigneeId = state.editingDraftAssigneeId,
                        )
                    } else {
                        it
                    }
                },
                error = null,
            ).withoutDraftEdit()
        }
    }

    fun cancelSubtaskDraftEdit() {
        _uiState.update { it.withoutDraftEdit() }
    }

    fun removeSubtaskDraft(id: String) {
        _uiState.update { state ->
            val remaining = state.subtaskDrafts.filterNot { it.id == id }
            val next = state.copy(subtaskDrafts = remaining, error = null)
            if (state.editingDraftId == id) next.withoutDraftEdit() else next
        }
    }

    fun reminderLaterToday() = chooseReminder { laterToday(clock()) }

    fun reminderTomorrow() = chooseReminder { tomorrowAt(clock(), timeZone.current(), defaultTime()) }

    fun reminderNextWeek() = chooseReminder { nextWeekAt(clock(), timeZone.current(), defaultTime()) }

    fun reminderCustom(local: LocalDateTime) = chooseReminder { dateTimeConverter.toInstant(local) }

    fun clearReminderDraft() {
        _uiState.update { it.copy(reminderDraft = null, reminderDraftLocal = null, reminderError = null, error = null) }
    }

    private suspend fun defaultTime(): LocalTime = preferences.defaultReminderTime.first()

    private fun chooseReminder(remindAt: suspend () -> Instant) {
        viewModelScope.launch {
            val instant = remindAt()
            val failure = validateRemindAt(instant, clock())
            _uiState.update {
                if (failure == null) {
                    it.copy(
                        reminderDraft = instant,
                        reminderDraftLocal = dateTimeConverter.toLocalDateTime(instant),
                        reminderError = null,
                        error = null,
                    )
                } else {
                    it.copy(reminderError = failure.message)
                }
            }
        }
    }

    private fun TaskEditorUiState.withoutDraftEdit(): TaskEditorUiState = copy(
        editingDraftId = null,
        editingDraftTitle = "",
        editingDraftDueAtLocal = null,
        editingDraftAssigneeId = null,
    )

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
                repository.createTaskWithExtras(state.columnId, state.toCreation(draft))
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
        val state = _uiState.value
        val taskId = state.taskId ?: return
        if (state.isSaving) {
            return
        }
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.deleteTask(taskId)) {
                is DomainResult.Success -> dismiss()
                is DomainResult.Failure ->
                    _uiState.update { it.copy(isSaving = false, error = result.error) }
            }
        }
    }

    fun dismiss() {
        _uiState.value = TaskEditorUiState()
    }

    private fun TaskEditorUiState.toCreation(task: TaskDraft) = TaskCreation(
        task = task,
        subtasks = subtaskDrafts.map {
            SubtaskDraft(
                title = it.title,
                assigneeId = it.assigneeId,
                dueAt = it.dueAtLocal?.let(dateTimeConverter::toInstant),
            )
        },
        remindAt = reminderDraft,
    )

    private fun Task.toEditorState(): TaskEditorUiState = TaskEditorUiState(
        isOpen = true,
        taskId = id,
        columnId = columnId,
        title = title,
        description = description.orEmpty(),
        priority = priority,
        dueAtLocal = dueAt?.let(dateTimeConverter::toLocalDateTime),
        assigneeId = assigneeId ?: LocalUser.ID,
    )
}
