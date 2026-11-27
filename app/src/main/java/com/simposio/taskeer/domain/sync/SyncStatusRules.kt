package com.simposio.taskeer.domain.sync

import java.time.Instant

data class SyncSnapshot(
    val signedIn: Boolean,
    val paused: PauseReason? = null,
    val syncing: Boolean = false,
    val pendingCount: Int = 0,
    val stuckCount: Int = 0,
    val lastSyncedAt: Instant? = null,
    val cycleError: SyncErrorKind? = null,
)

object SyncStatusRules {
    // Entradas que ya fallaron esta cantidad de veces sin remedio dejan de reintentarse y cuentan como error.
    const val STUCK_THRESHOLD: Int = 5

    fun resolve(snapshot: SyncSnapshot): SyncStatus = when {
        !snapshot.signedIn -> SyncStatus.Paused(PauseReason.NO_SESSION)
        snapshot.paused != null -> SyncStatus.Paused(snapshot.paused)
        snapshot.syncing -> SyncStatus.Syncing
        snapshot.stuckCount > 0 -> SyncStatus.Error(SyncErrorKind.STUCK_ENTRIES)
        snapshot.cycleError != null -> SyncStatus.Error(snapshot.cycleError)
        snapshot.pendingCount > 0 -> SyncStatus.Pending(snapshot.pendingCount)
        else -> SyncStatus.Idle(snapshot.lastSyncedAt)
    }

    // Sin sesión no hay nada que mostrar: la sincronización no existe para el usuario.
    fun isVisible(status: SyncStatus): Boolean = !(status is SyncStatus.Paused && status.reason == PauseReason.NO_SESSION)
}
