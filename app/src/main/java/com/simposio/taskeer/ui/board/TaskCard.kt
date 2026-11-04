package com.simposio.taskeer.ui.board

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.ui.components.IconLabel
import com.simposio.taskeer.ui.components.MyDayToggle
import com.simposio.taskeer.ui.tasks.DUE_DATE_TIME_FORMATTER
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import java.time.Instant
import java.time.LocalDateTime

@Composable
internal fun TaskCard(
    card: TaskCardUi,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    onToggleMyDay: (String) -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (elevated) 8.dp else 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.CardPadding, vertical = Spacing.ItemGap),
            verticalArrangement = Arrangement.spacedBy(Spacing.ItemGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                MyDayToggle(inMyDay = card.inMyDay, onToggle = { onToggleMyDay(card.id) })
            }
            val priorityColor = card.priority.color()
            IconLabel(
                icon = Icons.Filled.Flag,
                text = stringResource(card.priority.labelRes()),
                color = priorityColor,
                style = MaterialTheme.typography.labelMedium,
            )
            card.dueAtLocal?.let {
                val formatted = it.format(DUE_DATE_TIME_FORMATTER)
                if (card.isOverdue) {
                    IconLabel(
                        icon = Icons.Filled.Warning,
                        text = formatted,
                        color = MaterialTheme.colorScheme.error,
                        contentDescription = stringResource(R.string.task_overdue_description, formatted),
                    )
                } else {
                    IconLabel(icon = Icons.Filled.Schedule, text = formatted)
                }
            }
            card.assigneeId?.let { id ->
                val label = if (card.assigneeIsMe) stringResource(R.string.task_assignee_me) else id
                IconLabel(icon = Icons.Filled.Person, text = label)
            }
        }
    }
}

@Composable
internal fun TaskPriority.color(): Color = when (this) {
    TaskPriority.LOW -> MaterialTheme.colorScheme.secondary
    TaskPriority.MEDIUM -> MaterialTheme.colorScheme.primary
    TaskPriority.HIGH -> MaterialTheme.colorScheme.tertiary
    TaskPriority.URGENT -> MaterialTheme.colorScheme.error
}

@StringRes
internal fun TaskPriority.labelRes(): Int = when (this) {
    TaskPriority.LOW -> R.string.task_priority_low
    TaskPriority.MEDIUM -> R.string.task_priority_medium
    TaskPriority.HIGH -> R.string.task_priority_high
    TaskPriority.URGENT -> R.string.task_priority_urgent
}

@Preview(showBackground = true)
@Composable
private fun TaskCardPreview() {
    TaskeerTheme {
        TaskCard(
            card = TaskCardUi(
                id = "t1",
                title = "Escribir el informe",
                priority = TaskPriority.HIGH,
                dueAt = Instant.EPOCH,
                dueAtLocal = LocalDateTime.of(2026, 10, 22, 12, 30),
                assigneeId = "local-user",
                assigneeIsMe = true,
                inMyDay = true,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OverdueTaskCardPreview() {
    TaskeerTheme {
        TaskCard(
            card = TaskCardUi(
                id = "t2",
                title = "Entregar la memoria",
                priority = TaskPriority.URGENT,
                dueAt = Instant.EPOCH,
                dueAtLocal = LocalDateTime.of(2026, 10, 1, 9, 0),
                assigneeId = null,
                assigneeIsMe = false,
                inMyDay = false,
                isOverdue = true,
            ),
        )
    }
}