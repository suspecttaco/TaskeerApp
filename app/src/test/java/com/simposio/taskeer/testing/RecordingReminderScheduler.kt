package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.ReminderScheduler
import java.time.Instant

class RecordingReminderScheduler : ReminderScheduler {

    val scheduled: MutableList<Instant> = mutableListOf()

    var cancelCalls: Int = 0
        private set

    val current: Instant? get() = scheduled.lastOrNull()

    override fun scheduleAt(at: Instant) {
        scheduled += at
    }

    override fun cancel() {
        cancelCalls++
        scheduled.clear()
    }
}
