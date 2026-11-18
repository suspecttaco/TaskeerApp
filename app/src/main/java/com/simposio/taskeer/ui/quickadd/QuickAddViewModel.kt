package com.simposio.taskeer.ui.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.TaskListRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.TaskCreation
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.planned.dayLabelFor
import com.simposio.taskeer.domain.task.dueForQuickAdd
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class QuickAddViewModel(
    private val taskRepository: TaskRepository,
    taskListRepository: TaskListRepository,
    private val dateTimeConverter: DateTimeConverter,
    private val today: () -> LocalDate = { dateTimeConverter.toLocalDateTime(Instant.now()).toLocalDate() },
) : ViewModel() {

    private val local = MutableStateFlow(Local())

    // Una lista abierta que se borra con la píldora abierta cae a la lista por defecto.
    private val effectiveContext: StateFlow<QuickAddContext> = combine(
        local,
        taskListRepository.observeLists(),
    ) { local, lists ->
        val context = local.context
        if (context is QuickAddContext.InList && lists.isNotEmpty() && lists.none { it.list.id == context.listId }) {
            QuickAddContext.Default
        } else {
            context
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, QuickAddContext.Default)

    val uiState: StateFlow<QuickAddUiState> = local
        .combine(effectiveContext) { local, _ ->
            QuickAddUiState(
                isOpen = local.isOpen,
                title = local.title,
                dueDate = local.dueDate,
                dueLabel = local.dueDate?.let { dayLabelFor(it, today()) },
                isSaving = local.isSaving,
                error = local.error,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), QuickAddUiState())

    fun setContext(context: QuickAddContext) {
        local.update { it.copy(context = context) }
    }

    fun open() {
        local.update { it.copy(isOpen = true, dueDate = null) }
    }

    fun openWithDate(date: LocalDate?) {
        local.update { it.copy(isOpen = true, dueDate = date) }
    }

    fun close() {
        local.update { it.copy(isOpen = false, title = "", dueDate = null, error = null) }
    }

    fun onBack() = close()

    fun onTitleChange(value: String) {
        local.update { it.copy(title = value, error = null) }
    }

    fun clearDate() {
        local.update { it.copy(dueDate = null) }
    }

    fun add() {
        val current = local.value
        val title = current.title.trim()
        if (title.isEmpty() || current.isSaving) return
        val context = effectiveContext.value
        local.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val draft = TaskDraft(
                title = title,
                priority = TaskPriority.MEDIUM,
                dueAt = dueForQuickAdd(current.dueDate, null)?.let(dateTimeConverter::toInstant),
            )
            val creation = TaskCreation(draft, addToMyDay = context is QuickAddContext.MyDay)
            val listId = (context as? QuickAddContext.InList)?.listId
            when (val result = taskRepository.createTaskWithExtras(null, creation, listId)) {
                is DomainResult.Failure -> local.update { it.copy(isSaving = false, error = result.error) }
                is DomainResult.Success -> local.update { it.copy(isSaving = false, title = "", error = null) }
            }
        }
    }

    private data class Local(
        val isOpen: Boolean = false,
        val context: QuickAddContext = QuickAddContext.Default,
        val title: String = "",
        val dueDate: LocalDate? = null,
        val isSaving: Boolean = false,
        val error: DomainError? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
