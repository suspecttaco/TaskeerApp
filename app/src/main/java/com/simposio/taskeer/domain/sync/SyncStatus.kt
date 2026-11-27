package com.simposio.taskeer.domain.sync

import java.time.Instant

sealed interface SyncStatus {
    data class Idle(val lastSyncedAt: Instant?) : SyncStatus

    data class Pending(val count: Int) : SyncStatus

    data object Syncing : SyncStatus

    data class Error(val kind: SyncErrorKind) : SyncStatus

    data class Paused(val reason: PauseReason) : SyncStatus
}

enum class PauseReason { NO_SESSION, ACCOUNT_MISMATCH, UPDATE_REQUIRED, SERVER_NOT_READY }

enum class SyncErrorKind { STUCK_ENTRIES, UNEXPECTED_RESPONSE }
