package com.simposio.taskeer.ui.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.TaskListRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.list.effectiveListId
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.TaskCreation
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.model.TaskListSummary
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.planned.dayLabelFor
import com.simposio.taskeer.domain.task.QuickDateChoice
import com.simposio.taskeer.domain.task.QuickDateShortcut
import com.simposio.taskeer.domain.task.quickDateOf
import com.simposio.taskeer.domain.task.quickDueFor
import com.simposio.taskeer.domain.task.toggled
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
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {

    private val local = MutableStateFlow(Local())

    private val lists: StateFlow<List<TaskListSummary>> = taskListRepository.observeLists()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Una lista abierta que se borra con la píldora abierta cae a la lista por defecto.
    private val effectiveContext: StateFlow<QuickAddContext> = combine(local, lists) { local, lists ->
        val context = local.context
        if (context is QuickAddContext.InList && lists.isNotEmpty() && lists.none { it.list.id == context.listId }) {
            QuickAddContext.Default
        } else {
            context
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, QuickAddContext.Default)

    val uiState: StateFlow<QuickAddUiState> = combine(local, effectiveContext, lists) { local, context, lists ->
        val today = today()
        val selectedId = effectiveListId(lists, local.chosenListId, context.listId())
        val dueDate = quickDateOf(local.dateChoice, today)
        QuickAddUiState(
            isOpen = local.isOpen,
            title = local.title,
            dateChoice = local.dateChoice,
            dueDate = dueDate,
            dueLabel = dueDate?.let { dayLabelFor(it, today) },
            listOptions = lists.map { QuickAddListOption(it.list.id, it.list.name, it.activeCount, it.list.id == selectedId) },
            selectedListName = lists.firstOrNull { it.list.id == selectedId }?.list?.name,
            isListPanelOpen = local.isListPanelOpen,
            isDatePickerOpen = local.isDatePickerOpen,
            isSaving = local.isSaving,
            error = local.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), QuickAddUiState())

    fun setContext(context: QuickAddContext) {
        local.update { it.copy(context = context) }
    }

    fun open() {
        local.update { it.opened(QuickDateChoice.None) }
    }

    fun openWithDate(date: LocalDate?) {
        local.update { it.opened(date?.let(QuickDateChoice::Picked) ?: QuickDateChoice.None) }
    }

    fun close() {
        local.update { it.closed() }
    }

    fun onBack() {
        val current = local.value
        when {
            current.isDatePickerOpen -> dismissDatePicker()
            current.isListPanelOpen -> local.update { it.copy(isListPanelOpen = false) }
            else -> close()
        }
    }

    fun onTitleChange(value: String) {
        local.update { it.copy(title = value, error = null) }
    }

    fun selectShortcut(shortcut: QuickDateShortcut) {
        local.update { it.copy(dateChoice = toggled(it.dateChoice, shortcut)) }
    }

    fun openDatePicker() {
        local.update { it.copy(isDatePickerOpen = true) }
    }

    fun pickDate(date: LocalDate) {
        local.update { it.copy(dateChoice = QuickDateChoice.Picked(date), isDatePickerOpen = false) }
    }

    fun dismissDatePicker() {
        local.update { it.copy(isDatePickerOpen = false) }
    }

    fun clearDate() {
        local.update { it.copy(dateChoice = QuickDateChoice.None) }
    }

    fun toggleListPanel() {
        local.update { it.copy(isListPanelOpen = !it.isListPanelOpen) }
    }

    fun selectList(id: String) {
        if (lists.value.none { it.list.id == id }) return
        local.update { it.copy(chosenListId = id, isListPanelOpen = false) }
    }

    fun add() {
        val current = local.value
        val title = current.title.trim()
        if (title.isEmpty() || current.isSaving) return
        val context = effectiveContext.value
        val listId = effectiveListId(lists.value, current.chosenListId, context.listId())
        local.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val draft = TaskDraft(
                title = title,
                priority = TaskPriority.MEDIUM,
                dueAt = quickDueFor(current.dateChoice, today())?.let(dateTimeConverter::toInstant),
            )
            val creation = TaskCreation(draft, addToMyDay = context is QuickAddContext.MyDay)
            when (val result = taskRepository.createTaskWithExtras(null, creation, listId)) {
                is DomainResult.Failure -> local.update { it.copy(isSaving = false, error = result.error) }
                is DomainResult.Success -> local.update {
                    it.copy(isSaving = false, title = "", error = null, isListPanelOpen = false)
                }
            }
        }
    }

    private fun today(): LocalDate = dateTimeConverter.toLocalDateTime(clock()).toLocalDate()

    private fun QuickAddContext.listId(): String? = (this as? QuickAddContext.InList)?.listId

    private data class Local(
        val isOpen: Boolean = false,
        val context: QuickAddContext = QuickAddContext.Default,
        val title: String = "",
        val dateChoice: QuickDateChoice = QuickDateChoice.None,
        val chosenListId: String? = null,
        val isListPanelOpen: Boolean = false,
        val isDatePickerOpen: Boolean = false,
        val isSaving: Boolean = false,
        val error: DomainError? = null,
    ) {
        fun opened(choice: QuickDateChoice): Local = copy(
            isOpen = true,
            dateChoice = choice,
            chosenListId = null,
            isListPanelOpen = false,
            isDatePickerOpen = false,
        )

        fun closed(): Local = copy(
            isOpen = false,
            title = "",
            dateChoice = QuickDateChoice.None,
            chosenListId = null,
            isListPanelOpen = false,
            isDatePickerOpen = false,
            error = null,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
