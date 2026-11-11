package com.simposio.taskeer.ui.inbox

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.TaskPriority

data class InboxTaskUi(
    val id: String,
    val title: String,
    val priority: TaskPriority,
    val due: String?,
    val isOverdue: Boolean,
    val isMarked: Boolean,
)

data class InboxUiState(
    val items: List<InboxTaskUi> = emptyList(),
    val error: DomainError? = null,
)
