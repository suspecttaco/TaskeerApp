package com.simposio.taskeer.domain.sync

import java.time.Duration
import kotlin.random.Random

object BackoffRules {
    val INITIAL: Duration = Duration.ofSeconds(2)
    val MAX: Duration = Duration.ofMinutes(15)
    const val JITTER_FRACTION: Double = 0.2
    private const val MAX_DOUBLINGS = 20

    // `attempt` cuenta los fallos transitorios previos (0 = primer reintento). `jitter` va de -1 a 1.
    // Retry-After es un suelo: el servidor sabe cuándo admitirá la petición, el retroceso propio no puede adelantarse.
    fun delayFor(attempt: Int, retryAfter: Duration?, jitter: Double): Duration {
        val doublings = attempt.coerceIn(0, MAX_DOUBLINGS)
        val base = minOf(INITIAL.multipliedBy(1L shl doublings), MAX)
        val factor = 1.0 + JITTER_FRACTION * jitter.coerceIn(-1.0, 1.0)
        val jittered = Duration.ofMillis((base.toMillis() * factor).toLong())
        return if (retryAfter != null && retryAfter > jittered) retryAfter else jittered
    }

    fun jitterFrom(random: Random): Double = random.nextDouble() * 2 - 1
}
