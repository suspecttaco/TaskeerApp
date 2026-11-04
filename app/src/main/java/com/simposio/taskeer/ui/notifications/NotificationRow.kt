package com.simposio.taskeer.ui.notifications

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
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.AppNotificationKind
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.IconLabel
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
fun NotificationRow(
    row: NotificationRowUi,
    onOpen: (NotificationRowUi) -> Unit,
    onMarkRead: (String) -> Unit,
    onDelete: (String) -> Unit,
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
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (row.unread) FontWeight.Bold else FontWeight.Normal,
            )
            Text(text = row.text(), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = row.createdAtText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!row.delivered) {
                IconLabel(
                    icon = Icons.Filled.NotificationsOff,
                    text = stringResource(R.string.notifications_not_delivered),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        if (row.unread) {
            val unreadDescription = stringResource(R.string.notifications_unread_mark)
            Box(
                modifier = Modifier
                    .size(Spacing.DotSize)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .semantics { contentDescription = unreadDescription },
            )
            ActionIcon(
                icon = Icons.Filled.Done,
                contentDescription = stringResource(R.string.notifications_mark_read),
                onClick = { onMarkRead(row.id) },
            )
        }
        ActionIcon(
            icon = Icons.Filled.Delete,
            contentDescription = stringResource(R.string.notifications_delete),
            onClick = { onDelete(row.id) },
        )
    }
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
        )
    }
}
