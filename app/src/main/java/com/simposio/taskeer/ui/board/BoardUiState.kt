package com.simposio.taskeer.ui.board

import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.TaskPriority
import java.time.Instant
import java.time.LocalDateTime

data class BoardUiState(
    val columns: List<BoardColumnUi> = emptyList(),
    val error: DomainError? = null,
    val newColumn: NewColumnUiState = NewColumnUiState(),
)

data class NewColumnUiState(
    val isOpen: Boolean = false,
    val name: String = "",
    val error: DomainError? = null,
)

data class BoardColumnUi(
    val column: Column,
    val cards: List<TaskCardUi>,
) {
    val id: String get() = column.id
}

data class TaskCardUi(
    val id: String,
    val title: String,
    val priority: TaskPriority,
    val dueAt: Instant?,
    val dueAtLocal: LocalDateTime?,
    val assigneeId: String?,
    val assigneeIsMe: Boolean,
    val inMyDay: Boolean,
    val isOverdue: Boolean = false,
)
