package com.simposio.taskeer.domain.sync

sealed interface ManualSyncFeedback {
    data class Status(val status: SyncStatus) : ManualSyncFeedback

    data object Deferred : ManualSyncFeedback
}

object ManualSyncRules {
    // Sin sesión no hay ciclo que contar; el resto muestra el estado real, salvo el aplazado, que no tiene estado propio.
    fun result(outcome: CycleOutcome?, current: SyncStatus): ManualSyncFeedback? = when (outcome) {
        CycleOutcome.NO_SESSION -> null
        CycleOutcome.COMPLETED -> ManualSyncFeedback.Status(SyncStatus.Idle(null))
        CycleOutcome.STOPPED_BY_ERROR -> ManualSyncFeedback.Status(SyncStatus.Error(SyncErrorKind.UNEXPECTED_RESPONSE))
        CycleOutcome.PAUSED ->
            if (current is SyncStatus.Paused) ManualSyncFeedback.Status(current) else ManualSyncFeedback.Deferred
        CycleOutcome.DEFERRED, null -> when (current) {
            is SyncStatus.Error, is SyncStatus.Paused -> ManualSyncFeedback.Status(current)
            else -> ManualSyncFeedback.Deferred
        }
    }

    fun canRequest(status: SyncStatus, inFlight: Boolean): Boolean =
        !inFlight && status != SyncStatus.Syncing && SyncStatusRules.isVisible(status)
}
