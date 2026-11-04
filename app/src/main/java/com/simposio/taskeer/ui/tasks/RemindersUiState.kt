package com.simposio.taskeer.ui.tasks

import com.simposio.taskeer.domain.model.Reminder
import java.time.LocalDateTime

data class RemindersUiState(
    val taskId: String? = null,
    val reminder: Reminder? = null,
    val reminderAtLocal: LocalDateTime? = null,
    val error: String? = null,
)
