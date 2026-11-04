package com.simposio.taskeer.data.repository

import java.time.Instant

interface ReminderScheduler {
    fun scheduleAt(at: Instant)

    fun cancel()
}
