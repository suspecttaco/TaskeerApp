package com.simposio.taskeer.ui.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.BoardRepository
import com.simposio.taskeer.data.repository.ColumnRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.model.BoardDestination
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.TaskCreation
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.task.dueForQuickAdd
import com.simposio.taskeer.ui.tasks.observeBoardDestinations
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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
    private val dateTimeConverter: DateTimeConverter,
    private val today: () -> LocalDate = { dateTimeConverter.toLocalDateTime(Instant.now()).toLocalDate() },
) : ViewModel() {

    private val local = MutableStateFlow(Local())

    // Las columnas de cierre no se ofrecen: una tarea creada ahí ya estaría completada y no se vería en Mi día.
    val uiState: StateFlow<QuickAddUiState> = combine(
        observeBoardDestinations(boardRepository, columnRepository, taskRepository, includeDoneColumns = false),
        local,
    ) { destinations, local ->
        val wanted = local.destination ?: initialDestination(local.context, destinations)
        val label = (wanted as? QuickAddDestination.InColumn)?.let { destinationLabel(destinations, it.columnId) }
        QuickAddUiState(
            isOpen = local.isOpen,
            title = local.title,
            destination = if (label == null) QuickAddDestination.Inbox else wanted,
            destinationLabel = label,
            destinations = destinations,
            priority = local.priority,
            dueDate = local.dueDate,
            dueTime = local.dueTime,
            panel = local.panel,
            isSaving = local.isSaving,
            error = local.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), QuickAddUiState())

    fun open(context: QuickAddContext) {
        local.update { it.copy(isOpen = true, context = context, panel = null) }
    }

    fun close() {
        local.update { it.copy(isOpen = false, panel = null) }
    }

    fun onBack(): BackResult {
        if (local.value.panel != null) {
            local.update { it.copy(panel = null) }
            return BackResult.COLLAPSE_PANEL
        }
        close()
        return BackResult.CLOSE
    }

    fun onTitleChange(value: String) {
        local.update { it.copy(title = value, error = null) }
    }

    fun togglePanel(panel: QuickAddPanel) {
        local.update { it.copy(panel = if (it.panel == panel) null else panel) }
    }

    fun selectInbox() {
        local.update { it.copy(destination = QuickAddDestination.Inbox, panel = null, error = null) }
    }

    fun selectColumn(columnId: String) {
        local.update { it.copy(destination = QuickAddDestination.InColumn(columnId), panel = null, error = null) }
    }

    fun selectPriority(priority: TaskPriority) {
        local.update { it.copy(priority = priority, panel = null) }
    }

    // «Hoy» se resuelve al elegir y no al abrir: el sheet puede quedar abierto al cruzar medianoche.
    fun selectPreset(preset: QuickAddDatePreset) {
        when (preset) {
            QuickAddDatePreset.TODAY -> selectDate(today())
            QuickAddDatePreset.TOMORROW -> selectDate(today().plusDays(1))
            QuickAddDatePreset.NEXT_WEEK -> selectDate(today().plusDays(NEXT_WEEK_DAYS))
            QuickAddDatePreset.NONE -> selectDate(null)
        }
    }

    fun selectDate(date: LocalDate?) {
        local.update {
            it.copy(dueDate = date, dueTime = if (date == null) null else it.dueTime, panel = null)
        }
    }

    fun selectTime(time: LocalTime?) {
        local.update { it.copy(dueTime = time, panel = null) }
    }

    fun add() {
        val state = uiState.value
        if (state.title.isBlank() || state.isSaving) return
        val context = local.value.context
        val columnId = (state.destination as? QuickAddDestination.InColumn)?.columnId
        local.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val draft = TaskDraft(
                title = state.title,
                priority = state.priority,
                dueAt = dueForQuickAdd(state.dueDate, state.dueTime)?.let(dateTimeConverter::toInstant),
            )
            val creation = TaskCreation(draft, addToMyDay = context is QuickAddContext.MyDay)
            when (val result = taskRepository.createTaskWithExtras(columnId, creation)) {
                is DomainResult.Failure -> local.update { it.copy(isSaving = false, error = result.error) }
                is DomainResult.Success -> local.value = Local(context = context)
            }
        }
    }

    private fun initialDestination(context: QuickAddContext, destinations: List<BoardDestination>): QuickAddDestination {
        val boardId = (context as? QuickAddContext.Board)?.boardId ?: return QuickAddDestination.Inbox
        val first = destinations.firstOrNull { it.board.id == boardId }?.columns?.firstOrNull()
        return first?.let { QuickAddDestination.InColumn(it.column.id) } ?: QuickAddDestination.Inbox
    }

    private fun destinationLabel(destinations: List<BoardDestination>, columnId: String): String? {
        destinations.forEach { board ->
            board.columns.firstOrNull { it.column.id == columnId }?.let { return "${board.board.name} › ${it.column.name}" }
        }
        return null
    }

    private data class Local(
        val isOpen: Boolean = false,
        val context: QuickAddContext = QuickAddContext.Inbox,
        val title: String = "",
        val destination: QuickAddDestination? = null,
        val priority: TaskPriority = TaskPriority.MEDIUM,
        val dueDate: LocalDate? = null,
        val dueTime: LocalTime? = null,
        val panel: QuickAddPanel? = null,
        val isSaving: Boolean = false,
        val error: DomainError? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
        const val NEXT_WEEK_DAYS = 7L
    }
}
