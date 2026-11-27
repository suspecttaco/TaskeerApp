package com.simposio.taskeer.ui.notifications

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.notification.ServerNotificationType
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.AppDivider
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.EmptyState
import com.simposio.taskeer.ui.components.LoadingBody
import com.simposio.taskeer.ui.components.PhaseCrossfade
import com.simposio.taskeer.ui.components.PhaseKind
import com.simposio.taskeer.ui.components.StatePage
import com.simposio.taskeer.ui.components.StatePageAction
import com.simposio.taskeer.ui.components.phaseKindOf
import com.simposio.taskeer.ui.components.rememberWhile
import com.simposio.taskeer.ui.components.rowItemModifier
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import com.simposio.taskeer.ui.theme.unread

@Composable
fun AccountNotificationsContent(
    state: AccountNotificationsUiState,
    onRefresh: () -> Unit,
    onOpen: (AccountRowUi) -> Unit,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onDelete: (String) -> Unit,
    onRequestDeleteRead: () -> Unit,
    onConfirmDeleteRead: () -> Unit,
    onCancelDeleteRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(Spacing.ScreenPadding)) {
        val actions = NotificationsScreenRules.accountActions(state.unreadCount, state.readCount)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (actions.markAllRead) {
                ActionIcon(
                    icon = Icons.Filled.DoneAll,
                    contentDescription = stringResource(R.string.inbox_mark_all_read),
                    onClick = onMarkAllRead,
                )
            }
            if (actions.deleteRead) {
                ActionIcon(
                    icon = Icons.Filled.DeleteSweep,
                    contentDescription = stringResource(R.string.inbox_delete_read),
                    onClick = onRequestDeleteRead,
                )
            }
        }
        val shownError = rememberWhile(state.phase == AccountPhase.ERROR, state.error)
        PhaseCrossfade(
            kind = phaseKindOf(loading = state.phase == AccountPhase.LOADING, error = state.phase == AccountPhase.ERROR),
            modifier = Modifier.fillMaxSize(),
        ) { kind ->
            when (kind) {
                PhaseKind.LOADING -> LoadingBody(stringResource(R.string.inbox_loading))
                PhaseKind.ERROR -> StatePage(
                    icon = Icons.Filled.CloudOff,
                    title = stringResource(R.string.inbox_error_title),
                    message = (shownError ?: InboxErrorMessage.Generic).text(),
                    action = StatePageAction(stringResource(R.string.inbox_retry), onRefresh),
                )
                PhaseKind.CONTENT -> AccountList(
                    state = state,
                    onRefresh = onRefresh,
                    onOpen = onOpen,
                    onMarkRead = onMarkRead,
                    onDelete = onDelete,
                )
            }
        }
    }
    if (state.confirmDeleteRead) {
        AlertDialog(
            onDismissRequest = onCancelDeleteRead,
            title = { Text(stringResource(R.string.inbox_delete_read_title)) },
            text = { Text(stringResource(R.string.inbox_delete_read_message)) },
            confirmButton = { TextButton(onClick = onConfirmDeleteRead) { Text(stringResource(R.string.inbox_delete)) } },
            dismissButton = { TextButton(onClick = onCancelDeleteRead) { Text(stringResource(R.string.inbox_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountList(
    state: AccountNotificationsUiState,
    onRefresh: () -> Unit,
    onOpen: (AccountRowUi) -> Unit,
    onMarkRead: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (state.phase == AccountPhase.EMPTY) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Inbox,
                        text = stringResource(R.string.inbox_empty),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(state.rows, key = { it.id }) { row ->
                Column(modifier = rowItemModifier()) {
                    AccountNotificationRow(row = row, onOpen = onOpen, onMarkRead = onMarkRead, onDelete = onDelete)
                    AppDivider()
                }
            }
        }
    }
}

@Composable
private fun AccountNotificationRow(
    row: AccountRowUi,
    onOpen: (AccountRowUi) -> Unit,
    onMarkRead: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val security = ServerNotificationRules.highlightsSecurity(row.type)
    val tint = if (security) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val unreadState = stringResource(R.string.inbox_unread_state)
    ListItem(
        modifier = Modifier
            .clickable { onOpen(row) }
            .semantics(mergeDescendants = true) { if (row.unread) stateDescription = unreadState },
        leadingContent = { DecorativeIcon(icon = ServerNotificationRules.icon(row.type).vector(), tint = tint) },
        overlineContent = {
            Text(
                text = stringResource(ServerNotificationRules.labelRes(row.type)),
                color = if (security) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = {
            Text(text = row.title, style = LocalTextStyle.current.unread(row.unread))
        },
        supportingContent = {
            Column {
                row.body?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
                Text(
                    text = row.createdAtText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.unread) {
                    Box(modifier = Modifier.size(Spacing.DotSize).background(MaterialTheme.colorScheme.primary, CircleShape))
                    ActionIcon(
                        icon = Icons.Filled.Done,
                        contentDescription = stringResource(R.string.inbox_mark_read),
                        onClick = { onMarkRead(row.id) },
                    )
                }
                ActionIcon(
                    icon = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.inbox_delete),
                    onClick = { onDelete(row.id) },
                )
            }
        },
    )
}

@Composable
internal fun InboxErrorMessage.text(): String = text(LocalResources.current)

internal fun InboxErrorMessage.text(resources: Resources): String = when (this) {
    is InboxErrorMessage.RateLimited ->
        if (minutes != null) {
            resources.getQuantityString(R.plurals.inbox_error_rate_limited_minutes, minutes, minutes)
        } else {
            resources.getString(R.string.inbox_error_rate_limited)
        }
    InboxErrorMessage.Offline -> resources.getString(R.string.inbox_error_offline)
    InboxErrorMessage.ServerDown -> resources.getString(R.string.inbox_error_server)
    InboxErrorMessage.Generic -> resources.getString(R.string.inbox_error_generic)
}

private fun InboxIcon.vector(): ImageVector = when (this) {
    InboxIcon.ALARM -> Icons.Filled.Alarm
    InboxIcon.ASSIGNMENT -> Icons.Filled.AssignmentInd
    InboxIcon.ROLE -> Icons.Filled.ManageAccounts
    InboxIcon.SECURITY -> Icons.Filled.Security
    InboxIcon.INVITATION -> Icons.Filled.Mail
    InboxIcon.MENTION -> Icons.Filled.AlternateEmail
    InboxIcon.GENERIC -> Icons.Filled.Notifications
}

@Preview(showBackground = true)
@Composable
private fun AccountNotificationsContentPreview() {
    TaskeerTheme {
        AccountNotificationsContent(
            state = AccountNotificationsUiState(
                tabs = listOf(NotificationsTab.DEVICE, NotificationsTab.ACCOUNT),
                phase = AccountPhase.LIST,
                rows = listOf(
                    AccountRowUi("1", ServerNotificationType.TASK_ASSIGNED, "Revisar el informe", "Ana te asignó una tarea", "t1", "26 nov 2026, 9:00", true),
                    AccountRowUi("2", ServerNotificationType.RECOVERY_CODE_USED, "Código de recuperación usado", null, null, "25 nov 2026, 18:30", false),
                ),
                unreadCount = 1,
                readCount = 1,
            ),
            onRefresh = {},
            onOpen = {},
            onMarkRead = {},
            onMarkAllRead = {},
            onDelete = {},
            onRequestDeleteRead = {},
            onConfirmDeleteRead = {},
            onCancelDeleteRead = {},
        )
    }
}
