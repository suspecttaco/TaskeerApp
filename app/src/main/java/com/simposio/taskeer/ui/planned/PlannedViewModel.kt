package com.simposio.taskeer.ui.planned

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.PlannedRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.planned.PlannedItem
import com.simposio.taskeer.domain.planned.PlannedView
import com.simposio.taskeer.domain.task.isOverdue
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlannedViewModel(
    repository: PlannedRepository,
    private val taskRepository: TaskRepository,
    private val dateTimeConverter: DateTimeConverter,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    private val error = MutableStateFlow<DomainError?>(null)

    val uiState: StateFlow<PlannedUiState> = combine(repository.observePlanned(), error) { view, error ->
        PlannedUiState(sections = view.toSections(now()), error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PlannedUiState())

    fun toggleCompleted(taskId: String) {
        viewModelScope.launch {
            when (val result = taskRepository.setCompleted(taskId, true)) {
                is DomainResult.Failure -> error.value = result.error
                is DomainResult.Success -> error.value = null
            }
        }
    }

    private fun PlannedView.toSections(now: Instant): List<PlannedSectionUi> = listOfNotNull(
        PlannedSectionUi(PlannedSectionKind.OVERDUE, items = overdue.map { it.toUi(now) })
            .takeIf { overdue.isNotEmpty() },
        PlannedSectionUi(PlannedSectionKind.TODAY, items = today.map { it.toUi(now) })
            .takeIf { today.isNotEmpty() },
        PlannedSectionUi(PlannedSectionKind.TOMORROW, items = tomorrow.map { it.toUi(now) })
            .takeIf { tomorrow.isNotEmpty() },
        PlannedSectionUi(
            PlannedSectionKind.UPCOMING,
            days = upcoming.map { day ->
                PlannedDayUi(day.date, day.date.format(DATE_FORMATTER), day.items.map { it.toUi(now) })
            },
        ).takeIf { upcoming.isNotEmpty() },
    )

    private fun PlannedItem.toUi(now: Instant): PlannedTaskUi = PlannedTaskUi(
        id = task.id,
        title = task.title,
        priority = task.priority,
        boardName = boardName,
        time = task.dueAt?.let { dateTimeConverter.toLocalDateTime(it).format(TIME_FORMATTER) }.orEmpty(),
        isOverdue = isOverdue(task.dueAt, task.isCompleted, now),
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
    }
}
