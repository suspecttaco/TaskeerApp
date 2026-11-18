package com.simposio.taskeer.ui.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.data.repository.TaskListRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.task.RescheduleTarget
import com.simposio.taskeer.domain.task.isOverdue
import com.simposio.taskeer.ui.components.RowAction
import com.simposio.taskeer.ui.tasks.DUE_DATE_TIME_FORMATTER
import com.simposio.taskeer.ui.tasks.CompletionSettler
import com.simposio.taskeer.ui.tasks.TaskRescheduler
import com.simposio.taskeer.ui.tasks.settleOrder
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ListViewModel(
    private val taskRepository: TaskRepository,
    taskListRepository: TaskListRepository,
    private val myDayRepository: MyDayRepository,
    private val dateTimeConverter: DateTimeConverter,
    savedStateHandle: SavedStateHandle,
    private val rescheduler: TaskRescheduler = TaskRescheduler(taskRepository, dateTimeConverter),
    settleMillis: () -> Long = { 0L },
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    val listId: String = requireNotNull(savedStateHandle[LIST_ID_ARG]) { "Falta el listId de la ruta" }

    private val error = MutableStateFlow<DomainError?>(null)
    private val settler = CompletionSettler(viewModelScope, settleMillis)
    private var shownItems: List<ListTaskUi> = emptyList()

    val uiState: StateFlow<ListUiState> = combine(
        taskRepository.observeList(listId),
        taskListRepository.observeLists(),
        myDayRepository.observeMarkedTaskIds(),
        error,
        settler.settling,
    ) { tasks, lists, marked, error, settling ->
        val instant = now()
        val (pending, completed) = tasks.partition { !it.isCompleted }
        val list = lists.firstOrNull { it.list.id == listId }
        ListUiState(
            listName = list?.list?.name,
            items = settleOrder(
                previous = shownItems,
                next = (pending + completed).map { it.toUi(it.id in marked, instant) },
                settling = settling,
                id = { it.id },
            ).also { shownItems = it },
            missing = lists.isNotEmpty() && list == null,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ListUiState())

    fun toggleMarked(taskId: String) {
        val isMarked = uiState.value.items.firstOrNull { it.id == taskId }?.isMarked ?: return
        viewModelScope.launch {
            when (val result = myDayRepository.setMarked(taskId, !isMarked)) {
                is DomainResult.Failure -> error.value = result.error
                is DomainResult.Success -> error.value = null
            }
        }
    }

    fun toggleCompleted(taskId: String) {
        val completed = uiState.value.items.firstOrNull { it.id == taskId }?.isCompleted ?: return
        if (!completed) settler.begin(taskId)
        viewModelScope.launch {
            when (val result = taskRepository.setCompleted(taskId, !completed)) {
                is DomainResult.Failure -> error.value = result.error
                is DomainResult.Success -> error.value = null
            }
        }
    }

    // PICK_DATE, ASSIGN_TO_BOARD y MOVE_TO_LIST abren superficies de la pantalla y no pasan por aquí.
    fun onRowAction(taskId: String, action: RowAction) {
        when (action) {
            RowAction.COMPLETE, RowAction.REOPEN -> toggleCompleted(taskId)
            RowAction.ADD_TO_MY_DAY, RowAction.REMOVE_FROM_MY_DAY -> toggleMarked(taskId)
            RowAction.RESCHEDULE_TOMORROW -> reschedule(taskId, RescheduleTarget.TOMORROW)
            RowAction.RESCHEDULE_NEXT_WEEK -> reschedule(taskId, RescheduleTarget.NEXT_WEEK)
            RowAction.RESCHEDULE_SOMEDAY -> reschedule(taskId, RescheduleTarget.SOMEDAY)
            else -> Unit
        }
    }

    fun reschedule(taskId: String, target: RescheduleTarget) = execute { rescheduler.reschedule(taskId, target) }

    fun rescheduleTo(taskId: String, date: LocalDate) = execute { rescheduler.rescheduleTo(taskId, date) }

    private fun execute(action: suspend () -> DomainResult<Unit>) {
        viewModelScope.launch {
            when (val result = action()) {
                is DomainResult.Failure -> error.value = result.error
                is DomainResult.Success -> error.value = null
            }
        }
    }

    private fun Task.toUi(isMarked: Boolean, now: Instant): ListTaskUi = ListTaskUi(
        id = id,
        title = title,
        priority = priority,
        due = dueAt?.let { dateTimeConverter.toLocalDateTime(it).format(DUE_DATE_TIME_FORMATTER) },
        isOverdue = isOverdue(dueAt, isCompleted, now),
        isCompleted = isCompleted,
        isMarked = isMarked,
        isRepeating = repeat != null,
    )

    companion object {
        const val LIST_ID_ARG = "listId"
        private const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
