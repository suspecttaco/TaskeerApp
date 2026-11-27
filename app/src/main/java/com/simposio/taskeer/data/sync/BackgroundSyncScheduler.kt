package com.simposio.taskeer.data.sync

import java.time.Duration

interface BackgroundSyncScheduler {
    fun enqueuePush()

    fun enqueueRetry(delay: Duration)

    fun enqueuePeriodicPull(interval: Duration)

    fun cancelAll()
}
