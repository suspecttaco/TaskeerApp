package com.simposio.taskeer.domain.model

sealed interface NotificationEvent {
    data class Deadline(val taskId: String, val taskTitle: String) : NotificationEvent

    data class Digest(val activeTaskCount: Int) : NotificationEvent

    data class Manual(
        val reminderId: String,
        val taskId: String,
        val taskTitle: String,
    ) : NotificationEvent
}
