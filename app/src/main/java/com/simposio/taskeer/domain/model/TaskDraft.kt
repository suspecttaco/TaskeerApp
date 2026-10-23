package com.simposio.taskeer.domain.model

import java.time.Instant

data class TaskDraft(
    val title: String,
    val description: String? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    // Instante UTC: la conversión desde la fecha local la hace la capa de presentación.
    val dueAt: Instant? = null,
    val assigneeId: String? = null,
)
