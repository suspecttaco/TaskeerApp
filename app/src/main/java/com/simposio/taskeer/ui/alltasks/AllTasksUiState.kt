package com.simposio.taskeer.ui.alltasks

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.planned.PlannedGroup
import com.simposio.taskeer.ui.upcoming.PlannedTaskUi

data class AllTasksGroupUi(val group: PlannedGroup, val items: List<PlannedTaskUi>) {
    val count: Int get() = items.size
}

data class AllTasksUiState(
    val groups: List<AllTasksGroupUi> = emptyList(),
    val isEmpty: Boolean = true,
    val error: DomainError? = null,
)
