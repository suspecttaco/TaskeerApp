package com.simposio.taskeer.data.sync

import java.time.Duration

// Despierta al proceso cuando muera: el retraso lo calcula el motor, aquí solo se persiste en WorkManager.
class BackgroundRetryScheduler(
    private val scheduler: BackgroundSyncScheduler,
    private val active: () -> Boolean,
) : RetryScheduler {
    override fun schedule(delay: Duration) {
        if (active()) scheduler.enqueueRetry(delay)
    }
}

class CompositeRetryScheduler(private vararg val schedulers: RetryScheduler) : RetryScheduler {
    override fun schedule(delay: Duration) {
        schedulers.forEach { it.schedule(delay) }
    }
}
