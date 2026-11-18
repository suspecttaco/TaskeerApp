package com.simposio.taskeer.ui.myday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.MyDayTask
import com.simposio.taskeer.domain.myday.MyDayFilter
import com.simposio.taskeer.domain.myday.MyDayItem
import com.simposio.taskeer.domain.myday.MyDaySort
import com.simposio.taskeer.domain.myday.applyMyDay
import com.simposio.taskeer.domain.myday.mergeMyDay
import com.simposio.taskeer.domain.myday.suggestionsFor
import com.simposio.taskeer.domain.task.RescheduleTarget
import com.simposio.taskeer.domain.task.isOverdue
import com.simposio.taskeer.ui.components.RowAction
import com.simposio.taskeer.ui.components.RowContext
import com.simposio.taskeer.ui.tasks.DUE_DATE_TIME_FORMATTER
import com.simposio.taskeer.ui.tasks.TaskRescheduler
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MyDayViewModel(
    private val repository: MyDayRepository,
    private val taskRepository: TaskRepository,
    private val dateTimeConverter: DateTimeConverter,
    private val rescheduler: TaskRescheduler = TaskRescheduler(taskRepository, dateTimeConverter),
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    private val error = MutableStateFlow<DomainError?>(null)
    private val sort = MutableStateFlow(MyDaySort.DUE_DATE)
    private val filter = MutableStateFlow(MyDayFilter.ALL)
    private val suggestionsExpanded = MutableStateFlow(true)

    private data class Controls(
        val sort: MyDaySort,
        val filter: MyDayFilter,
        val suggestionsExpanded: Boolean,
        val error: DomainError?,
    )

    private val controls = combine(sort, filter, suggestionsExpanded, error, ::Controls)

    val uiState: StateFlow<MyDayUiState> = combine(
        repository.observeMyDay(),
        repository.observeOverdueCount(),
        controls,
    ) { myDay, overdueCount, controls ->
        val instant = now()
        val all = mergeMyDay(myDay)
        MyDayUiState(
            items = applyMyDay(all, controls.filter, controls.sort, instant).map { it.toUi(instant) },
            sort = controls.sort,
            filter = controls.filter,
            overdueCount = overdueCount,
            totalCount = all.size,
            completedCount = all.count { it.task.isCompleted },
            suggestions = suggestionsFor(myDay)
                .filterNot { it.task.isCompleted }
                .map { it.toSuggestionUi(instant) },
            suggestionsExpanded = controls.suggestionsExpanded,
            error = controls.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MyDayUiState())

    fun setSort(value: MyDaySort) {
        sort.value = value
    }

    fun setFilter(value: MyDayFilter) {
        filter.value = value
    }

    fun toggleSuggestions() {
        suggestionsExpanded.value = !suggestionsExpanded.value
    }

    fun addSuggestion(taskId: String) = setMarked(taskId, true)

    fun dismissSuggestion(taskId: String) = setMarked(taskId, false)

    private fun setMarked(taskId: String, marked: Boolean) {
        viewModelScope.launch {
            when (val result = repository.setMarked(taskId, marked)) {
                is DomainResult.Failure -> error.value = result.error
                is DomainResult.Success -> error.value = null
            }
        }
    }

    fun toggleMarked(taskId: String) {
        val isMarked = uiState.value.items.firstOrNull { it.id == taskId }?.isMarked ?: return
        setMarked(taskId, !isMarked)
    }

    fun toggleCompleted(taskId: String) {
        val completed = uiState.value.items.firstOrNull { it.id == taskId }?.isCompleted ?: return
        setCompleted(taskId, !completed)
    }

    private fun setCompleted(taskId: String, completed: Boolean) {
        viewModelScope.launch {
            when (val result = taskRepository.setCompleted(taskId, completed)) {
                is DomainResult.Failure -> error.value = result.error
                is DomainResult.Success -> error.value = null
            }
        }
    }

    // PICK_DATE y ASSIGN_TO_BOARD abren superficies de la pantalla y no pasan por aquí.
    fun onRowAction(context: RowContext, taskId: String, action: RowAction) {
        if (context == RowContext.SUGGESTION) {
            when (action) {
                RowAction.KEEP_TODAY -> addSuggestion(taskId)
                RowAction.DISMISS -> dismissSuggestion(taskId)
                RowAction.COMPLETE -> completeSuggestion(taskId)
                RowAction.RESCHEDULE_TOMORROW -> suggestionTomorrow(taskId)
                else -> Unit
            }
            return
        }
        when (action) {
            RowAction.COMPLETE, RowAction.REOPEN -> toggleCompleted(taskId)
            RowAction.REMOVE_FROM_MY_DAY -> toggleMarked(taskId)
            RowAction.RESCHEDULE_TOMORROW -> reschedule(taskId, RescheduleTarget.TOMORROW)
            RowAction.RESCHEDULE_NEXT_WEEK -> reschedule(taskId, RescheduleTarget.NEXT_WEEK)
            RowAction.RESCHEDULE_SOMEDAY -> reschedule(taskId, RescheduleTarget.SOMEDAY)
            else -> Unit
        }
    }

    fun completeSuggestion(taskId: String) = setCompleted(taskId, true)

    // Reprograma y la saca de hoy: si solo se reprogramara, seguiría sugerida mañana por su marca antigua.
    fun suggestionTomorrow(taskId: String) {
        viewModelScope.launch {
            val result = rescheduler.reschedule(taskId, RescheduleTarget.TOMORROW)
            if (result is DomainResult.Success) setMarked(taskId, false) else publish(result)
        }
    }

    fun reschedule(taskId: String, target: RescheduleTarget) {
        val wasMarked = uiState.value.items.firstOrNull { it.id == taskId }?.isMarked == true
        viewModelScope.launch {
            val result = rescheduler.reschedule(taskId, target)
            if (result is DomainResult.Success && target == RescheduleTarget.TOMORROW && wasMarked) {
                setMarked(taskId, false)
            } else {
                publish(result)
            }
        }
    }

    fun rescheduleTo(taskId: String, date: LocalDate) {
        viewModelScope.launch { publish(rescheduler.rescheduleTo(taskId, date)) }
    }

    private fun publish(result: DomainResult<Unit>) {
        error.value = (result as? DomainResult.Failure)?.error
    }

    private fun MyDayTask.toSuggestionUi(now: Instant): MyDaySuggestionUi = MyDaySuggestionUi(
        id = task.id,
        title = task.title,
        boardName = boardName,
        listName = listName,
        priority = task.priority,
        due = task.dueAt?.let { dateTimeConverter.toLocalDateTime(it).format(DUE_DATE_TIME_FORMATTER) },
        isOverdue = isOverdue(task.dueAt, task.isCompleted, now),
        isRepeating = task.repeat != null,
    )

    private fun MyDayItem.toUi(now: Instant): MyDayTaskUi = MyDayTaskUi(
        id = task.id,
        title = task.title,
        priority = task.priority,
        boardName = boardName,
        listName = listName,
        due = task.dueAt?.let { dateTimeConverter.toLocalDateTime(it).format(DUE_DATE_TIME_FORMATTER) },
        isOverdue = isOverdue(task.dueAt, task.isCompleted, now),
        isCompleted = task.isCompleted,
        isMarked = isMarked,
        isAssigned = isAssigned,
        isRepeating = task.repeat != null,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
