package com.simposio.taskeer.ui.quickadd

import com.simposio.taskeer.domain.model.BoardDestination
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.TaskPriority
import java.time.LocalDate
import java.time.LocalTime

sealed interface QuickAddDestination {
    data object Inbox : QuickAddDestination

    data class InColumn(val columnId: String) : QuickAddDestination
}

enum class QuickAddPanel { DESTINATION, DATE, TIME, PRIORITY }

enum class QuickAddDatePreset { TODAY, TOMORROW, NEXT_WEEK, NONE }

sealed interface QuickAddContext {
    data object MyDay : QuickAddContext

    data object Inbox : QuickAddContext

    data class Board(val boardId: String) : QuickAddContext
}

enum class BackResult { COLLAPSE_PANEL, CLOSE }

data class QuickAddUiState(
    val isOpen: Boolean = false,
    val title: String = "",
    val destination: QuickAddDestination = QuickAddDestination.Inbox,
    val destinationLabel: String? = null,
    val destinations: List<BoardDestination> = emptyList(),
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val panel: QuickAddPanel? = null,
    val isSaving: Boolean = false,
    val error: DomainError? = null,
)
