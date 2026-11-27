package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.sync.BackgroundDecision
import com.simposio.taskeer.domain.sync.BackgroundKind
import com.simposio.taskeer.domain.sync.BackgroundSyncRules
import com.simposio.taskeer.domain.sync.CycleOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant

class BackgroundSyncRunner(
    private val session: StateFlow<SessionState>,
    private val enabled: suspend () -> Boolean,
    private val pendingCount: suspend () -> Int,
    private val lastSyncedAt: suspend () -> Instant?,
    private val cycle: suspend () -> CycleOutcome,
    private val clock: () -> Instant,
    private val sessionTimeout: Duration = BackgroundSyncRules.SESSION_WAIT,
) {
    suspend fun run(kind: BackgroundKind, runAttemptCount: Int): BackgroundDecision {
        if (!enabled()) return BackgroundDecision.SUCCESS
        val state = withTimeoutOrNull(sessionTimeout.toMillis()) { session.first { it !is SessionState.Resuming } }
            ?: return BackgroundSyncRules.sessionNotReady(runAttemptCount)
        if (state !is SessionState.SignedIn) return BackgroundDecision.SUCCESS
        return try {
            if (!BackgroundSyncRules.shouldRun(kind, pendingCount(), lastSyncedAt(), clock())) {
                BackgroundDecision.SUCCESS
            } else {
                BackgroundSyncRules.decide(cycle())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            BackgroundDecision.FAILURE
        }
    }
}
