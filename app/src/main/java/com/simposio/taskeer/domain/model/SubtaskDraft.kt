package com.simposio.taskeer.domain.model

import java.time.Instant

data class SubtaskDraft(
    val title: String,
    val assigneeId: String? = null,
    val dueAt: Instant? = null,
)
