package com.simposio.taskeer.ui.tasks

import com.simposio.taskeer.domain.model.BoardDestination
import com.simposio.taskeer.domain.model.DomainError

data class AssignToBoardUiState(
    val taskId: String? = null,
    val destinations: List<BoardDestination> = emptyList(),
    val error: DomainError? = null,
) {
    val isOpen: Boolean get() = taskId != null
}
