package com.simposio.taskeer.ui.lists

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.TaskPriority

data class ListTaskUi(
    val id: String,
    val title: String,
    val priority: TaskPriority,
    val due: String?,
    val isOverdue: Boolean,
    val isMarked: Boolean,
    val isCompleted: Boolean = false,
    val isRepeating: Boolean = false,
)

data class ListUiState(
    val listName: String? = null,
    val items: List<ListTaskUi> = emptyList(),
    val missing: Boolean = false,
    val error: DomainError? = null,
)
