package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.sync.BackgroundSyncRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

// Mantiene encolado el trabajo en segundo plano solo mientras hay sesión y el ajuste está activo.
class BackgroundSyncController(
    private val session: StateFlow<SessionState>,
    private val enabled: Flow<Boolean>,
    private val pendingCount: Flow<Int>,
    private val scheduler: BackgroundSyncScheduler,
) {
    private val mutableActive = MutableStateFlow(false)

    val active: StateFlow<Boolean> = mutableActive.asStateFlow()

    suspend fun run() {
        // Resuming no es ni sesión ni su pérdida: se ignora para no cancelar el trabajo mientras el proceso restaura la sesión.
        val signedIn = session.map { state ->
            when (state) {
                is SessionState.SignedIn -> true
                is SessionState.SignedOut -> false
                SessionState.Resuming -> null
            }
        }.filterNotNull()
        combine(signedIn, enabled) { isSignedIn, isEnabled -> isSignedIn && isEnabled }
            .distinctUntilChanged()
            .collectLatest { isActive ->
                mutableActive.value = isActive
                if (!isActive) {
                    scheduler.cancelAll()
                    return@collectLatest
                }
                scheduler.enqueuePeriodicPull(BackgroundSyncRules.PULL_INTERVAL)
                pendingCount.map { it > 0 }.distinctUntilChanged().collect { hasPending ->
                    if (hasPending) scheduler.enqueuePush()
                }
            }
    }
}
