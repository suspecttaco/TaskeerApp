package com.simposio.taskeer.domain.sync

import java.time.Duration
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackoffRulesTest {

    @Test
    fun secuencia_duplicaHastaQuinceMinutos() {
        val seconds = (0..10).map { BackoffRules.delayFor(it, null, jitter = 0.0).seconds }

        assertEquals(listOf(2L, 4, 8, 16, 32, 64, 128, 256, 512, 900, 900), seconds)
    }

    @Test
    fun retryAfter_esSuelo() {
        assertEquals(Duration.ofSeconds(120), BackoffRules.delayFor(0, Duration.ofSeconds(120), jitter = 0.0))
        assertEquals(Duration.ofSeconds(8), BackoffRules.delayFor(2, Duration.ofSeconds(1), jitter = 0.0))
    }

    @Test
    fun jitter_dentroDelVeintePorCiento() {
        val low = BackoffRules.delayFor(3, null, jitter = -1.0).toMillis()
        val high = BackoffRules.delayFor(3, null, jitter = 1.0).toMillis()

        assertEquals(12_800L, low)
        assertEquals(19_200L, high)
        repeat(50) {
            val sample = BackoffRules.delayFor(3, null, BackoffRules.jitterFrom(Random(it))).toMillis()
            assertTrue(sample in low..high)
        }
    }

    @Test
    fun intentosEnormes_noDesbordan() {
        assertEquals(Duration.ofMinutes(15), BackoffRules.delayFor(10_000, null, jitter = 0.0))
    }
}
