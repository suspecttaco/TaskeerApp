package com.simposio.taskeer.domain.model

import java.time.Instant

data class TaskCreation(
    val task: TaskDraft,
    val subtasks: List<SubtaskDraft> = emptyList(),
    val remindAt: Instant? = null,
    val addToMyDay: Boolean = false,
)
