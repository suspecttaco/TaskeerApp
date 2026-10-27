package com.simposio.taskeer.ui.tasks

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.Subtask

data class SubtasksUiState(
    val taskId: String? = null,
    val items: List<Subtask> = emptyList(),
    val newTitle: String = "",
    val editingId: String? = null,
    val editingTitle: String = "",
    val error: DomainError? = null,
)
