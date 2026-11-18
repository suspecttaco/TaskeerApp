package com.simposio.taskeer.ui.alltasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.PlannedRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.planned.AddTarget
import com.simposio.taskeer.domain.planned.PlannedGroup
import com.simposio.taskeer.domain.planned.addTargetDate
import com.simposio.taskeer.domain.task.RescheduleTarget
import com.simposio.taskeer.ui.components.RowAction
import com.simposio.taskeer.ui.tasks.TaskRescheduler
import com.simposio.taskeer.ui.tasks.settleOrder
import com.simposio.taskeer.ui.upcoming.PlannedRowHandler
import com.simposio.taskeer.ui.upcoming.toPlannedTaskUi
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class AllTasksViewModel(
    repository: PlannedRepository,
    taskRepository: TaskRepository,
    private val dateTimeConverter: DateTimeConverter,
    rescheduler: TaskRescheduler = TaskRescheduler(taskRepository, dateTimeConverter),
    settleMillis: () -> Long = { 0L },
    private val now: () -> Instant = Instant::now,
    private val today: () -> LocalDate = { dateTimeConverter.toLocalDateTime(Instant.now()).toLocalDate() },
) : ViewModel() {

    private val error = MutableStateFlow<DomainError?>(null)
    private val handler = PlannedRowHandler(viewModelScope, taskRepository, rescheduler, error, settleMillis)
    private var shownGroups: List<AllTasksGroupUi> = emptyList()

    val uiState: StateFlow<AllTasksUiState> = combine(
        repository.observeAllTasks(),
        error,
        handler.settling,
    ) { view, error, settling ->
        val instant = now()
        val groups = listOf(
            PlannedGroup.TODAY to view.today,
            PlannedGroup.TOMORROW to view.tomorrow,
            PlannedGroup.UPCOMING to view.upcoming,
            PlannedGroup.SOMEDAY to view.someday,
        ).map { (group, items) ->
            val ui = items.map { it.toPlannedTaskUi(dateTimeConverter, instant) }
            val previous = shownGroups.firstOrNull { it.group == group }?.items.orEmpty()
            AllTasksGroupUi(group, settleOrder(previous, ui, settling, { it.id }, { it.copy(isCompleted = true) }))
        }
        shownGroups = groups
        AllTasksUiState(groups = groups, isEmpty = groups.all { it.items.isEmpty() }, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AllTasksUiState())

    // Se evalúa al tocar «+», no al abrir la pantalla: tras la medianoche usa el día nuevo.
    fun addDateFor(group: PlannedGroup): LocalDate? = addTargetDate(AddTarget.Group(group), today())

    fun onRowAction(taskId: String, action: RowAction) = handler.onRowAction(taskId, action)

    fun reschedule(taskId: String, target: RescheduleTarget) = handler.reschedule(taskId, target)

    fun rescheduleTo(taskId: String, date: LocalDate) = handler.rescheduleTo(taskId, date)

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
