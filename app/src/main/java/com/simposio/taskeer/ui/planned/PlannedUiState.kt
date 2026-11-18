package com.simposio.taskeer.ui.planned

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.TaskPriority
import java.time.LocalDate

enum class PlannedSectionKind { OVERDUE, TODAY, TOMORROW, UPCOMING }

data class PlannedTaskUi(
    val id: String,
    val title: String,
    val priority: TaskPriority,
    val boardName: String?,
    val time: String,
    val isOverdue: Boolean,
) {
    val isInbox: Boolean get() = boardName == null
}

data class PlannedDayUi(
    val date: LocalDate,
    val label: String,
    val items: List<PlannedTaskUi>,
)

// Las secciones sin fecha propia (vencidas, hoy, mañana) usan `items`; «próximos días» usa `days`.
data class PlannedSectionUi(
    val kind: PlannedSectionKind,
    val items: List<PlannedTaskUi> = emptyList(),
    val days: List<PlannedDayUi> = emptyList(),
)

data class PlannedUiState(
    val sections: List<PlannedSectionUi> = emptyList(),
    val error: DomainError? = null,
)
