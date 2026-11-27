package com.simposio.taskeer.ui.sync

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.sync.PauseReason
import com.simposio.taskeer.domain.sync.SyncErrorKind
import com.simposio.taskeer.domain.sync.SyncStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun syncStatusText(status: SyncStatus): String = when (status) {
    is SyncStatus.Idle -> status.lastSyncedAt?.let {
        val formatted = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(it)
        stringResource(R.string.sync_status_idle_at, formatted)
    } ?: stringResource(R.string.sync_status_idle)
    is SyncStatus.Pending -> stringResource(R.string.sync_status_pending, status.count)
    SyncStatus.Syncing -> stringResource(R.string.sync_status_syncing)
    is SyncStatus.Error -> when (status.kind) {
        SyncErrorKind.STUCK_ENTRIES -> stringResource(R.string.sync_status_error_stuck)
        SyncErrorKind.UNEXPECTED_RESPONSE -> stringResource(R.string.sync_status_error_unexpected)
    }
    is SyncStatus.Paused -> when (status.reason) {
        PauseReason.ACCOUNT_MISMATCH -> stringResource(R.string.sync_status_paused_account)
        PauseReason.UPDATE_REQUIRED -> stringResource(R.string.sync_status_paused_update)
        PauseReason.SERVER_NOT_READY -> stringResource(R.string.sync_status_paused_server)
        PauseReason.NO_SESSION -> stringResource(R.string.sync_status_idle)
    }
}
