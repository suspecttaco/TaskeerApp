package com.simposio.taskeer.ui.tasks

import java.time.LocalDateTime

data class ReminderActions(
    val onLaterToday: () -> Unit,
    val onTomorrow: () -> Unit,
    val onNextWeek: () -> Unit,
    val onCustom: (LocalDateTime) -> Unit,
    val onCancel: () -> Unit,
) {
    companion object {
        val None = ReminderActions({}, {}, {}, {}, {})
    }
}
