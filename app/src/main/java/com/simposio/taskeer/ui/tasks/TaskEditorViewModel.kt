package com.simposio.taskeer.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.BoardRepository
import com.simposio.taskeer.data.repository.ColumnRepository
import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.data.repository.TaskListRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.model.RepeatUnit
import com.simposio.taskeer.domain.model.SubtaskDraft
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskCreation
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.model.TaskRepeat
import com.simposio.taskeer.domain.model.newClientId
import com.simposio.taskeer.domain.reminder.laterToday
import com.simposio.taskeer.domain.reminder.nextWeekAt
import com.simposio.taskeer.domain.reminder.tomorrowAt
import com.simposio.taskeer.domain.reminder.validateRemindAt
import com.simposio.taskeer.domain.task.RepeatPreset
import com.simposio.taskeer.domain.task.RepeatPresets
import com.simposio.taskeer.domain.task.presetOf
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TaskEditorViewModel(
    private val repository: TaskRepository,
    private val dateTimeConverter: DateTimeConverter,
    private val preferences: PreferencesRepository,
    private val timeZone: TimeZoneProvider,
    private val listRepository: TaskListRepository,
    private val myDayRepository: MyDayRepository,
    private val boardRepository: BoardRepository,
    private val columnRepository: ColumnRepository,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskEditorUiState())

    val uiState: StateFlow<TaskEditorUiState> = _uiState.asStateFlow()

    private var contextJob: Job? = null

    fun openForCreate(columnId: String?, listId: String? = null) {
        val state = TaskEditorUiState(isOpen = true, columnId = columnId, listId = listId)
        _uiState.value = state.copy(initialFields = state.fields())
        observeContext()
    }

    fun openForEdit(taskId: String) {
        viewModelScope.launch {
            val result = repository.getTask(taskId)
            if (result is DomainResult.Success) {
                val state = result.value.toEditorState()
                _uiState.value = state.copy(initialFields = state.fields())
                observeContext()
            }
        }
    }

    // Un solo Job: los flujos de contexto viven mientras la hoja está abierta y no se mezclan con el borrador.
    private fun observeContext() {
        contextJob?.cancel()
        contextJob = viewModelScope.launch {
            launch {
                listRepository.observeLists().collect { lists -> _uiState.update { it.copy(lists = lists) } }
            }
            launch {
                combine(
                    boardRepository.observeBoards(),
                    columnRepository.observeAllColumns(),
                    _uiState.map { it.columnId }.distinctUntilChanged(),
                ) { boards, columns, columnId ->
                    val column = columns.firstOrNull { it.id == columnId }
                    boards.firstOrNull { it.id == column?.boardId }?.name to column?.name
                }.collect { (boardName, columnName) ->
                    _uiState.update { it.copy(boardName = boardName, columnName = columnName) }
                }
            }
            val taskId = _uiState.value.taskId
            if (taskId != null) {
                launch {
                    myDayRepository.observeMarkedTaskIds().collect { ids ->
                        _uiState.update { it.copy(inMyDay = taskId in ids) }
                    }
                }
            }
        }
    }

    fun onToggleCompleted() {
        val state = _uiState.value
        val taskId = state.taskId ?: return
        if (state.isSaving || state.isClosing) {
            return
        }
        viewModelScope.launch {
            when (val result = repository.setCompleted(taskId, !state.completed)) {
                is DomainResult.Success -> _uiState.update {
                    it.copy(completed = result.value.isCompleted, columnId = result.value.columnId, error = null)
                }
                is DomainResult.Failure -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    fun onToggleMyDay() {
        val state = _uiState.value
        val taskId = state.taskId
        if (taskId == null) {
            _uiState.update { it.copy(inMyDay = !it.inMyDay, error = null) }
            return
        }
        if (state.isSaving || state.isClosing) {
            return
        }
        viewModelScope.launch {
            val result = myDayRepository.setMarked(taskId, !state.inMyDay)
            if (result is DomainResult.Failure) {
                _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    fun onListChosen(listId: String) {
        val state = _uiState.value
        val taskId = state.taskId
        when {
            state.isBoardTask || listId == state.listId -> closePanel()
            taskId == null -> _uiState.update { it.copy(listId = listId, panel = EditorPanel.None, error = null) }
            else -> viewModelScope.launch {
                when (val result = repository.moveToList(taskId, listId)) {
                    is DomainResult.Success -> _uiState.update {
                        it.copy(listId = result.value.listId, panel = EditorPanel.None, error = null)
                    }
                    is DomainResult.Failure -> _uiState.update { it.copy(error = result.error) }
                }
            }
        }
    }

    fun openPanel(panel: EditorPanel) {
        val target = if (panel is EditorPanel.DateTime) EditorPanel.DateTime(panel.target) else panel
        _uiState.update { it.copy(panel = target, error = null) }
    }

    fun closePanel() {
        _uiState.update { it.copy(panel = EditorPanel.None) }
    }

    fun onPickerDate(epochDay: Long) {
        _uiState.update { state ->
            val panel = state.panel as? EditorPanel.DateTime ?: return@update state
            state.copy(panel = panel.copy(pickedEpochDay = epochDay))
        }
    }

    // Aplica el valor a lo que el editor posee (fecha, borradores); en una tarea guardada el recordatorio
    // y la subtarea viven en sus propios ViewModels, así que se devuelve el valor para que el host lo encamine.
    fun onPickerTime(time: LocalTime): PickedDateTime? {
        val state = _uiState.value
        val panel = state.panel as? EditorPanel.DateTime ?: return null
        val epochDay = panel.pickedEpochDay ?: return null
        val local = LocalDateTime.of(LocalDate.ofEpochDay(epochDay), time)
        closePanel()
        val saved = state.taskId != null
        return when (panel.target) {
            PickerTarget.TASK_DUE -> null.also { onDueAtChange(local) }
            PickerTarget.REMINDER ->
                if (saved) PickedDateTime(panel.target, local) else null.also { reminderCustom(local) }
            PickerTarget.SUBTASK_DUE ->
                if (saved) PickedDateTime(panel.target, local) else null.also { onEditSubtaskDueAtChange(local) }
        }
    }

    fun onClearDue() {
        onDueAtChange(null)
        closePanel()
    }

    fun onBack() {
        val panel = _uiState.value.panel
        if (panel == EditorPanel.None) {
            requestClose()
        } else {
            _uiState.update { it.copy(panel = panel.parent()) }
        }
    }

    fun requestClose() {
        val state = _uiState.value
        if (state.isClosing) {
            return
        }
        if (state.isDirty) {
            _uiState.update { it.copy(panel = EditorPanel.DiscardConfirm) }
        } else {
            close()
        }
    }

    fun confirmDiscard() = close()

    fun keepEditing() = closePanel()

    fun close() {
        _uiState.update { it.copy(isClosing = true) }
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
        _uiState.update { state ->
            when {
                local == null -> state.copy(
                    dueAtLocal = null,
                    repeat = null,
                    repeatCustom = false,
                    repeatIntervalText = "1",
                    error = null,
                )
                state.repeat == null -> state.copy(dueAtLocal = local, error = null)
                else -> state.copy(
                    dueAtLocal = local,
                    repeat = state.repeat.copy(anchorDay = anchorDayFor(state, local)),
                    error = null,
                )
            }
        }
    }

    fun onRepeatPresetChange(preset: RepeatPreset) {
        _uiState.update { state ->
            val due = state.dueAtLocal ?: return@update state
            val anchor = anchorDayFor(state, due)
            when (preset) {
                RepeatPreset.NONE -> state.copy(
                    repeat = null,
                    repeatCustom = false,
                    repeatIntervalText = "1",
                    error = null,
                )
                RepeatPreset.CUSTOM -> {
                    val rule = state.repeat?.copy(anchorDay = anchor) ?: TaskRepeat(RepeatUnit.DAY, 1, emptySet(), anchor)
                    state.copy(
                        repeat = rule,
                        repeatCustom = true,
                        repeatIntervalText = rule.interval.toString(),
                        error = null,
                    )
                }
                else -> state.copy(
                    repeat = RepeatPresets.of(preset, anchor),
                    repeatCustom = false,
                    repeatIntervalText = "1",
                    error = null,
                )
            }
        }
    }

    // Un texto que no es un entero queda como intervalo 0 para que el dominio lo rechace al guardar.
    fun onRepeatIntervalChange(text: String) {
        _uiState.update { state ->
            val rule = state.repeat ?: return@update state
            state.copy(
                repeat = rule.copy(interval = text.toIntOrNull() ?: 0),
                repeatIntervalText = text,
                error = null,
            )
        }
    }

    fun onRepeatUnitChange(unit: RepeatUnit) {
        _uiState.update { state ->
            val rule = state.repeat ?: return@update state
            val weekdays = if (unit == RepeatUnit.WEEK) rule.weekdays else emptySet()
            state.copy(repeat = rule.copy(unit = unit, weekdays = weekdays), error = null)
        }
    }

    fun onRepeatWeekdayToggle(day: DayOfWeek) {
        _uiState.update { state ->
            val rule = state.repeat?.takeIf { it.unit == RepeatUnit.WEEK } ?: return@update state
            val weekdays = if (day in rule.weekdays) rule.weekdays - day else rule.weekdays + day
            state.copy(repeat = rule.copy(weekdays = weekdays), error = null)
        }
    }

    private fun anchorDayFor(state: TaskEditorUiState, due: LocalDateTime): Int {
        val initial = state.initialRepeat
        val sameDate = due.toLocalDate() == state.initialDueAtLocal?.toLocalDate()
        return if (initial != null && sameDate) initial.anchorDay else due.dayOfMonth
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
            repeat = state.repeat,
        )
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val result = if (state.taskId == null) {
                repository.createTaskWithExtras(
                    state.columnId,
                    state.toCreation(draft),
                    state.listId.takeIf { state.columnId == null },
                )
            } else {
                repository.updateTask(state.taskId, draft)
            }
            when (result) {
                is DomainResult.Success -> _uiState.update { it.copy(isClosing = true) }
                is DomainResult.Failure ->
                    _uiState.update { it.copy(isSaving = false, error = result.error) }
            }
        }
    }

    fun dismiss() {
        contextJob?.cancel()
        contextJob = null
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
        addToMyDay = inMyDay,
    )

    private fun Task.toEditorState(): TaskEditorUiState = TaskEditorUiState(
        isOpen = true,
        taskId = id,
        columnId = columnId,
        listId = listId,
        completed = isCompleted,
        title = title,
        description = description.orEmpty(),
        priority = priority,
        dueAtLocal = dueAt?.let(dateTimeConverter::toLocalDateTime),
        assigneeId = assigneeId ?: LocalUser.ID,
        repeat = repeat,
        repeatCustom = presetOf(repeat) == RepeatPreset.CUSTOM,
        repeatIntervalText = repeat?.interval?.toString() ?: "1",
        initialDueAtLocal = dueAt?.let(dateTimeConverter::toLocalDateTime),
        initialRepeat = repeat,
    )
}

data class PickedDateTime(val target: PickerTarget, val local: LocalDateTime)
