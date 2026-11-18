package com.simposio.taskeer.ui.upcoming

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.planned.DayLabel
import java.time.LocalDate

data class PlannedTaskUi(
    val id: String,
    val title: String,
    val priority: TaskPriority,
    val boardName: String?,
    val time: String,
    val isOverdue: Boolean,
    val isRepeating: Boolean = false,
    val listName: String? = null,
) {
    val isInbox: Boolean get() = boardName == null
}

data class UpcomingDayUi(val date: LocalDate, val label: DayLabel, val items: List<PlannedTaskUi>)

data class UpcomingUiState(
    val days: List<UpcomingDayUi> = emptyList(),
    val isEmpty: Boolean = true,
    val error: DomainError? = null,
)
