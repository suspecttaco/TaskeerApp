package com.simposio.taskeer.domain.sync

import java.time.Duration
import java.time.Instant

enum class CycleOutcome { COMPLETED, DEFERRED, PAUSED, NO_SESSION, STOPPED_BY_ERROR }

enum class BackgroundKind { PUSH, RETRY, PULL }

enum class BackgroundDecision { SUCCESS, RETRY, FAILURE }

object BackgroundSyncRules {
    val PULL_INTERVAL: Duration = Duration.ofHours(6)
    val FRESH_WINDOW: Duration = Duration.ofMinutes(5)
    val SESSION_WAIT: Duration = Duration.ofSeconds(30)
    const val MAX_SESSION_ATTEMPTS = 3

    fun shouldRun(kind: BackgroundKind, pending: Int, lastSyncedAt: Instant?, now: Instant): Boolean = when (kind) {
        BackgroundKind.PUSH, BackgroundKind.RETRY -> pending > 0
        BackgroundKind.PULL -> lastSyncedAt == null || Duration.between(lastSyncedAt, now) >= FRESH_WINDOW
    }

    // Todo resultado del motor es éxito: el retroceso y las pausas ya los gestiona el motor, no WorkManager.
    fun decide(outcome: CycleOutcome): BackgroundDecision = when (outcome) {
        CycleOutcome.COMPLETED,
        CycleOutcome.DEFERRED,
        CycleOutcome.PAUSED,
        CycleOutcome.NO_SESSION,
        CycleOutcome.STOPPED_BY_ERROR,
        -> BackgroundDecision.SUCCESS
    }

    fun sessionNotReady(runAttemptCount: Int): BackgroundDecision =
        if (runAttemptCount < MAX_SESSION_ATTEMPTS) BackgroundDecision.RETRY else BackgroundDecision.SUCCESS
}
