package com.simposio.taskeer.ui.sync

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.sync.SyncStatus

@Composable
internal fun SyncSettingsSection(state: SyncUiState, onSync: () -> Unit, modifier: Modifier = Modifier) {
    if (!state.visible) return
    ListItem(
        modifier = modifier,
        headlineContent = { Text(stringResource(R.string.sync_title)) },
        supportingContent = {
            Text(syncStatusText(state.status), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        },
        trailingContent = {
            TextButton(onClick = onSync, enabled = state.status != SyncStatus.Syncing) {
                Text(stringResource(R.string.sync_action))
            }
        },
    )
}

@Composable
internal fun BackgroundSyncRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        headlineContent = { Text(stringResource(R.string.sync_background_title)) },
        supportingContent = { Text(stringResource(R.string.sync_background_supporting)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
internal fun AccountSwitchDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_account_dialog_title)) },
        text = { Text(stringResource(R.string.sync_account_dialog_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.sync_account_dialog_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sync_account_dialog_cancel)) } },
    )
}
