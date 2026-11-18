package com.simposio.taskeer.ui.tasks

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.TaskListSummary

data class MoveToListUiState(
    val taskId: String? = null,
    val lists: List<TaskListSummary> = emptyList(),
    val error: DomainError? = null,
) {
    val isOpen: Boolean get() = taskId != null
}
