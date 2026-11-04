package com.simposio.taskeer.ui.notifications

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.AppNotificationKind
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
fun NotificationRow(
    row: NotificationRowUi,
    onOpen: (NotificationRowUi) -> Unit,
    onMarkRead: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = { onOpen(row) },
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.title(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (row.unread) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (row.unread) {
                    Text(
                        text = stringResource(R.string.notifications_unread_mark),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(text = row.text(), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = row.createdAtText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!row.delivered) {
                Text(
                    text = stringResource(R.string.notifications_not_delivered),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.unread) {
                    TextButton(onClick = { onMarkRead(row.id) }) {
                        Text(stringResource(R.string.notifications_mark_read))
                    }
                }
                TextButton(onClick = { onDelete(row.id) }) {
                    Text(stringResource(R.string.notifications_delete))
                }
            }
        }
    }
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
