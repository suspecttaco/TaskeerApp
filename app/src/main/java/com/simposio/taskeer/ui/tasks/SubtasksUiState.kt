package com.simposio.taskeer.ui.tasks

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.Subtask
import java.time.LocalDateTime

data class SubtasksUiState(
    val taskId: String? = null,
    val items: List<Subtask> = emptyList(),
    val newTitle: String = "",
    val editingId: String? = null,
    val editingTitle: String = "",
    val editingDueAtLocal: LocalDateTime? = null,
    val editingAssigneeId: String? = null,
    val error: DomainError? = null,
)
