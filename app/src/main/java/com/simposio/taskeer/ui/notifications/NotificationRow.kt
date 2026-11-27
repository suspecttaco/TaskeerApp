package com.simposio.taskeer.ui.notifications

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.AppNotificationKind
import com.simposio.taskeer.domain.reminder.SnoozeOption
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.IconLabel
import com.simposio.taskeer.ui.components.rememberAnimationsEnabled
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import com.simposio.taskeer.ui.theme.stateFadeEnter
import com.simposio.taskeer.ui.theme.stateFadeExit
import com.simposio.taskeer.ui.theme.unread

@Composable
fun NotificationRow(
    row: NotificationRowUi,
    onOpen: (NotificationRowUi) -> Unit,
    onMarkRead: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSnooze: (NotificationRowUi, SnoozeOption) -> Unit,
    onSnoozeCustom: (NotificationRowUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpen(row) }
            .padding(vertical = Spacing.ItemGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
    ) {
        DecorativeIcon(icon = row.kind.icon(), tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.ItemGap)) {
            Text(
                text = row.title(),
                style = MaterialTheme.typography.bodyLarge.unread(row.unread),
            )
            Text(text = row.text(), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = row.createdAtText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.snoozedUntilText?.let { until ->
                IconLabel(
                    icon = Icons.Filled.Snooze,
                    text = stringResource(R.string.notifications_snoozed_until, until),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (!row.delivered) {
                IconLabel(
                    icon = Icons.Filled.NotificationsOff,
                    text = stringResource(R.string.notifications_not_delivered),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        val animated = rememberAnimationsEnabled()
        val unreadDescription = stringResource(R.string.notifications_unread_mark)
        AnimatedVisibility(visible = row.unread, enter = stateFadeEnter(animated), exit = stateFadeExit(animated)) {
            Box(
                modifier = Modifier
                    .size(Spacing.DotSize)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .semantics { contentDescription = unreadDescription },
            )
        }
        if (row.unread) {
            ActionIcon(
                icon = Icons.Filled.Done,
                contentDescription = stringResource(R.string.notifications_mark_read),
                onClick = { onMarkRead(row.id) },
            )
        }
        if (row.canSnooze) {
            SnoozeAction(
                onSelect = { onSnooze(row, it) },
                onCustom = { onSnoozeCustom(row) },
            )
        }
        ActionIcon(
            icon = Icons.Filled.Delete,
            contentDescription = stringResource(R.string.notifications_delete),
            onClick = { onDelete(row.id) },
        )
    }
}

@Composable
private fun SnoozeAction(onSelect: (SnoozeOption) -> Unit, onCustom: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ActionIcon(
            icon = Icons.Filled.Snooze,
            contentDescription = stringResource(R.string.notifications_snooze),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SnoozeMenuItem(R.string.reminder_later_today) { expanded = false; onSelect(SnoozeOption.LATER_TODAY) }
            SnoozeMenuItem(R.string.reminder_tomorrow) { expanded = false; onSelect(SnoozeOption.TOMORROW) }
            SnoozeMenuItem(R.string.reminder_next_week) { expanded = false; onSelect(SnoozeOption.NEXT_WEEK) }
            SnoozeMenuItem(R.string.reminder_custom) { expanded = false; onCustom() }
        }
    }
}

@Composable
private fun SnoozeMenuItem(@StringRes label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}

private fun AppNotificationKind.icon(): ImageVector = when (this) {
    AppNotificationKind.DEADLINE -> Icons.Filled.Event
    AppNotificationKind.DIGEST -> Icons.Filled.Summarize
    AppNotificationKind.REMINDER -> Icons.Filled.Alarm
}

@Composable
private fun NotificationRowUi.title(): String = when (kind) {
    AppNotificationKind.DIGEST -> stringResource(R.string.digest_notification_title)
    else -> taskTitle.orEmpty()
}

@Composable
private fun NotificationRowUi.text(): String = when (kind) {
    AppNotificationKind.DEADLINE -> stringResource(R.string.deadline_notification_text)
    AppNotificationKind.REMINDER -> stringResource(R.string.reminder_notification_text)
    AppNotificationKind.DIGEST -> {
        val count = activeTaskCount ?: 0
        if (count == 0) {
            stringResource(R.string.digest_notification_none)
        } else {
            pluralStringResource(R.plurals.digest_notification_count, count, count)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationRowPreview() {
    TaskeerTheme {
        NotificationRow(
            row = NotificationRowUi(
                id = "n1",
                kind = AppNotificationKind.DEADLINE,
                taskId = "t1",
                taskTitle = "Entregar la memoria",
                activeTaskCount = null,
                createdAtText = "4 nov 2026, 9:00",
                unread = true,
                delivered = false,
            ),
            onOpen = {},
            onMarkRead = {},
            onDelete = {},
            onSnooze = { _, _ -> },
            onSnoozeCustom = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SnoozedNotificationRowPreview() {
    TaskeerTheme {
        NotificationRow(
            row = NotificationRowUi(
                id = "n2",
                kind = AppNotificationKind.REMINDER,
                taskId = "t1",
                taskTitle = "Llamar al banco",
                activeTaskCount = null,
                createdAtText = "4 nov 2026, 9:00",
                unread = false,
                delivered = true,
                snoozedUntilText = "5 nov 2026, 9:00",
            ),
            onOpen = {},
            onMarkRead = {},
            onDelete = {},
            onSnooze = { _, _ -> },
            onSnoozeCustom = {},
        )
    }
}
