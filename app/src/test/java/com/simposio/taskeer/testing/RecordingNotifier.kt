package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.ReminderNotifier
import com.simposio.taskeer.domain.model.NotificationEvent

class RecordingNotifier(var shown: Boolean = true) : ReminderNotifier {

    val events: MutableList<NotificationEvent> = mutableListOf()

    var onNotify: () -> Unit = {}

    override fun notify(event: NotificationEvent): Boolean {
        onNotify()
        events += event
        return shown
    }
}
