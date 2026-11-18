package com.simposio.taskeer.ui.upcoming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.PlannedRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.task.RescheduleTarget
import com.simposio.taskeer.ui.components.RowAction
import com.simposio.taskeer.ui.tasks.TaskRescheduler
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class UpcomingViewModel(
    repository: PlannedRepository,
    taskRepository: TaskRepository,
    private val dateTimeConverter: DateTimeConverter,
    rescheduler: TaskRescheduler = TaskRescheduler(taskRepository, dateTimeConverter),
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    private val error = MutableStateFlow<DomainError?>(null)
    private val handler = PlannedRowHandler(viewModelScope, taskRepository, rescheduler, error)

    val uiState: StateFlow<UpcomingUiState> = combine(repository.observeUpcoming(), error) { days, error ->
        val instant = now()
        UpcomingUiState(
            days = days.map { day ->
                UpcomingDayUi(day.date, day.label, day.items.map { it.toPlannedTaskUi(dateTimeConverter, instant) })
            },
            isEmpty = days.all { it.items.isEmpty() },
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UpcomingUiState())

    fun onRowAction(taskId: String, action: RowAction) = handler.onRowAction(taskId, action)

    fun reschedule(taskId: String, target: RescheduleTarget) = handler.reschedule(taskId, target)

    fun rescheduleTo(taskId: String, date: LocalDate) = handler.rescheduleTo(taskId, date)

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
