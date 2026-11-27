package com.simposio.taskeer.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.reminder.SnoozeOption
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.AppDivider
import com.simposio.taskeer.ui.components.EmptyState
import com.simposio.taskeer.ui.components.rowItemModifier
import com.simposio.taskeer.ui.theme.Spacing

@Composable
fun DeviceNotificationsContent(
    state: NotificationsUiState,
    onOpen: (NotificationRowUi) -> Unit,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
    onSnooze: (NotificationRowUi, SnoozeOption) -> Unit,
    onSnoozeCustom: (NotificationRowUi) -> Unit,
    permissionNotice: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
    ) {
        permissionNotice()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            ActionIcon(
                icon = Icons.Filled.DoneAll,
                contentDescription = stringResource(R.string.notifications_mark_all_read),
                onClick = onMarkAllRead,
                enabled = state.unreadCount > 0,
            )
            ActionIcon(
                icon = Icons.Filled.DeleteSweep,
                contentDescription = stringResource(R.string.notifications_delete_all),
                onClick = onDeleteAll,
                enabled = state.rows.isNotEmpty(),
            )
        }
        if (state.rows.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.NotificationsNone,
                text = stringResource(R.string.notifications_empty),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LazyColumn {
            items(state.rows, key = { it.id }) { row ->
                Column(modifier = rowItemModifier()) {
                    NotificationRow(
                        row = row,
                        onOpen = onOpen,
                        onMarkRead = onMarkRead,
                        onDelete = onDelete,
                        onSnooze = onSnooze,
                        onSnoozeCustom = onSnoozeCustom,
                    )
                    AppDivider()
                }
            }
        }
    }
}

@Composable
internal fun DeleteAllDialog(
    total: Int,
    unread: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notifications_delete_all_title)) },
        text = {
            Text(pluralStringResource(R.plurals.notifications_delete_all_message, total, total, unread))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.notifications_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.notifications_delete_all_cancel))
            }
        },
    )
}
