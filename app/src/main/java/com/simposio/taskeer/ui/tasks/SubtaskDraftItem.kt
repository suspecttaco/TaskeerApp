package com.simposio.taskeer.ui.tasks

import java.time.LocalDateTime

data class SubtaskDraftItem(
    val id: String,
    val title: String,
    val dueAtLocal: LocalDateTime? = null,
    val assigneeId: String? = null,
)
