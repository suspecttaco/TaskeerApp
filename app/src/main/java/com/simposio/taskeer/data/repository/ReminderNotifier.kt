package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.model.NotificationEvent

fun interface ReminderNotifier {
    fun notify(event: NotificationEvent): Boolean
}
