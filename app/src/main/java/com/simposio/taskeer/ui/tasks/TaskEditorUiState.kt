package com.simposio.taskeer.ui.tasks

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.TaskPriority
import java.time.LocalDateTime

data class TaskEditorUiState(
    val isOpen: Boolean = false,
    val taskId: String? = null,
    val columnId: String = "",
    val title: String = "",
    val description: String = "",
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueAtLocal: LocalDateTime? = null,
    val assigneeId: String? = null,
    val error: DomainError? = null,
    val isSaving: Boolean = false,
)
