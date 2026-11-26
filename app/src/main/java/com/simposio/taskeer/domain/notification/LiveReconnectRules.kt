package com.simposio.taskeer.domain.notification

object LiveReconnectRules {
    const val BASE_DELAY_MILLIS = 2_000L
    const val MAX_DELAY_MILLIS = 60_000L
    const val MAX_RETRY_AFTER_SECONDS = 900L
    const val CLEAN_RECONNECT_PAUSE_MILLIS = 1_000L
    const val STABLE_OPEN_MILLIS = 10_000L
    const val JITTER_RATIO = 0.2

    // `jitter` llega en [-1, 1]; el tope se aplica antes del jitter y `Retry-After` es un suelo, no un valor exacto.
    fun delayMillis(failures: Int, retryAfterSeconds: Long?, jitter: Double): Long {
        val exponent = (failures - 1).coerceIn(0, MAX_EXPONENT)
        val capped = minOf(MAX_DELAY_MILLIS, BASE_DELAY_MILLIS shl exponent)
        val spread = jitter.coerceIn(-1.0, 1.0) * JITTER_RATIO
        val jittered = (capped * (1.0 + spread)).toLong()
        val floor = retryAfterSeconds?.coerceIn(0L, MAX_RETRY_AFTER_SECONDS)?.times(1_000L) ?: 0L
        return maxOf(jittered, floor)
    }

    fun shouldResetFailures(openMillis: Long, receivedEvent: Boolean): Boolean =
        receivedEvent || openMillis >= STABLE_OPEN_MILLIS

    private const val MAX_EXPONENT = 10
}
