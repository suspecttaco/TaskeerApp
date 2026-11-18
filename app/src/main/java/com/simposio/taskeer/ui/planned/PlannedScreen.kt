package com.simposio.taskeer.ui.planned

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.ui.board.color
import com.simposio.taskeer.ui.board.labelRes
import com.simposio.taskeer.ui.components.CompleteCheckbox
import com.simposio.taskeer.ui.components.IconLabel
import com.simposio.taskeer.ui.components.SectionHeader
import com.simposio.taskeer.ui.tasks.rememberTaskEditorHost
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import java.time.LocalDate

@Composable
fun PlannedScreen(viewModelFactory: ViewModelProvider.Factory, modifier: Modifier = Modifier) {
    val viewModel: PlannedViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editorHost = rememberTaskEditorHost(viewModelFactory)
    PlannedContent(
        state = state,
        onOpenTask = editorHost.openForEdit,
        onToggleCompleted = viewModel::toggleCompleted,
        modifier = modifier,
    )
}

@Composable
fun PlannedContent(
    state: PlannedUiState,
    onOpenTask: (String) -> Unit,
    onToggleCompleted: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
    ) {
        state.error?.let { error ->
            Text(
                text = error.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.sections.isEmpty()) {
            Text(text = stringResource(R.string.planned_empty), style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap)) {
            state.sections.forEach { section ->
                item(key = "section-${section.kind}") {
                    SectionHeader(text = stringResource(section.kind.titleRes()))
                }
                if (section.kind == PlannedSectionKind.UPCOMING) {
                    section.days.forEach { day ->
                        item(key = "day-${day.date}") {
                            Text(
                                text = day.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        plannedRows(day.items, onOpenTask, onToggleCompleted)
                    }
                } else {
                    plannedRows(section.items, onOpenTask, onToggleCompleted)
                }
            }
        }
    }
}

private fun LazyListScope.plannedRows(
    tasks: List<PlannedTaskUi>,
    onOpen: (String) -> Unit,
    onToggleCompleted: (String) -> Unit,
) {
    tasks.forEach { task ->
        item(key = "task-${task.id}") { PlannedTaskCard(task, onOpen, onToggleCompleted) }
    }
}

@StringRes
private fun PlannedSectionKind.titleRes(): Int = when (this) {
    PlannedSectionKind.OVERDUE -> R.string.planned_overdue
    PlannedSectionKind.TODAY -> R.string.planned_today
    PlannedSectionKind.TOMORROW -> R.string.planned_tomorrow
    PlannedSectionKind.UPCOMING -> R.string.planned_upcoming
}

@Composable
private fun PlannedTaskCard(
    task: PlannedTaskUi,
    onOpen: (String) -> Unit,
    onToggleCompleted: (String) -> Unit,
) {
    Card(
        onClick = { onOpen(task.id) },
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.CardPadding, vertical = Spacing.ItemGap),
            verticalArrangement = Arrangement.spacedBy(Spacing.ItemGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompleteCheckbox(completed = false, onToggle = { onToggleCompleted(task.id) })
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
            }
            IconLabel(
                icon = Icons.Filled.Flag,
                text = stringResource(task.priority.labelRes()),
                color = task.priority.color(),
                style = MaterialTheme.typography.labelMedium,
            )
            if (task.isInbox) {
                IconLabel(icon = Icons.Filled.Inbox, text = stringResource(R.string.inbox_title))
            } else {
                IconLabel(icon = Icons.Filled.Dashboard, text = requireNotNull(task.boardName))
            }
            if (task.isOverdue) {
                IconLabel(
                    icon = Icons.Filled.Warning,
                    text = task.time,
                    color = MaterialTheme.colorScheme.error,
                    contentDescription = stringResource(R.string.task_overdue_description, task.time),
                )
            } else {
                IconLabel(icon = Icons.Filled.Schedule, text = task.time)
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun PlannedContentPreview() {
    TaskeerTheme {
        PlannedContent(
            state = PlannedUiState(
                sections = listOf(
                    PlannedSectionUi(
                        PlannedSectionKind.OVERDUE,
                        items = listOf(PlannedTaskUi("t1", "Entregar la memoria", TaskPriority.URGENT, "Proyecto", "9:00", true)),
                    ),
                    PlannedSectionUi(
                        PlannedSectionKind.TODAY,
                        items = listOf(PlannedTaskUi("t2", "Comprar pan", TaskPriority.MEDIUM, null, "18:00", false)),
                    ),
                    PlannedSectionUi(
                        PlannedSectionKind.UPCOMING,
                        days = listOf(
                            PlannedDayUi(
                                LocalDate.of(2026, 11, 20),
                                "viernes, 20 de noviembre de 2026",
                                listOf(PlannedTaskUi("t3", "Llamar al banco", TaskPriority.HIGH, "Casa", "10:30", false)),
                            ),
                        ),
                    ),
                ),
            ),
            onOpenTask = {},
            onToggleCompleted = {},
        )
    }
}
