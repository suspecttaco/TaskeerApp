package com.simposio.taskeer.ui.sync

import android.content.res.Resources
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.sync.ManualSyncFeedback
import com.simposio.taskeer.domain.sync.PauseReason
import com.simposio.taskeer.domain.sync.SyncErrorKind
import com.simposio.taskeer.domain.sync.SyncStatus
import com.simposio.taskeer.ui.components.NoticeKind

internal fun manualSyncText(feedback: ManualSyncFeedback, resources: Resources): String = when (feedback) {
    ManualSyncFeedback.Deferred -> resources.getString(R.string.sync_manual_deferred)
    is ManualSyncFeedback.Status -> when (val status = feedback.status) {
        is SyncStatus.Idle -> resources.getString(R.string.sync_status_idle)
        is SyncStatus.Pending -> resources.getString(R.string.sync_status_pending, status.count)
        SyncStatus.Syncing -> resources.getString(R.string.sync_status_syncing)
        is SyncStatus.Error -> when (status.kind) {
            SyncErrorKind.STUCK_ENTRIES -> resources.getString(R.string.sync_status_error_stuck)
            SyncErrorKind.UNEXPECTED_RESPONSE -> resources.getString(R.string.sync_status_error_unexpected)
        }
        is SyncStatus.Paused -> when (status.reason) {
            PauseReason.ACCOUNT_MISMATCH -> resources.getString(R.string.sync_status_paused_account)
            PauseReason.UPDATE_REQUIRED -> resources.getString(R.string.sync_status_paused_update)
            PauseReason.SERVER_NOT_READY -> resources.getString(R.string.sync_status_paused_server)
            PauseReason.NO_SESSION -> resources.getString(R.string.sync_status_idle)
        }
    }
}

internal fun noticeKindFor(feedback: ManualSyncFeedback): NoticeKind = when {
    feedback is ManualSyncFeedback.Status && (feedback.status is SyncStatus.Error || feedback.status is SyncStatus.Paused) ->
        NoticeKind.ERROR
    else -> NoticeKind.INFO
}
