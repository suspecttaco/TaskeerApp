package com.simposio.taskeer.domain.model

data class NewTaskBundle(
    val task: Task,
    val subtasks: List<Subtask>,
    val reminder: Reminder?,
)
