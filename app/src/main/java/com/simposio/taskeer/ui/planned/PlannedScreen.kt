package com.simposio.taskeer.ui.planned

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.ui.components.EmptyState
import com.simposio.taskeer.ui.components.RowAction
import com.simposio.taskeer.ui.components.RowContext
import com.simposio.taskeer.ui.components.SectionHeader
import com.simposio.taskeer.ui.components.TaskRow
import com.simposio.taskeer.ui.components.TaskRowUi
import com.simposio.taskeer.ui.components.actionsIn
import com.simposio.taskeer.ui.tasks.EmptyScreen
import com.simposio.taskeer.ui.tasks.SwipeHintState
import com.simposio.taskeer.ui.tasks.emptyStateFor
import com.simposio.taskeer.ui.tasks.rememberTaskActionsSheet
import com.simposio.taskeer.ui.tasks.rememberTaskEditorHost
import com.simposio.taskeer.ui.tasks.toRow
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import java.time.LocalDate

@Composable
fun PlannedScreen(
    viewModelFactory: ViewModelProvider.Factory,
    swipeHint: SwipeHintState,
    onGoToInbox: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PlannedViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editorHost = rememberTaskEditorHost(viewModelFactory)
    val openActions = rememberTaskActionsSheet(
        onAction = { _, id, action -> viewModel.onRowAction(id, action) },
        onPickDate = { _, id, date -> viewModel.rescheduleTo(id, date) },
    )
    PlannedContent(
        state = state,
        onOpenTask = editorHost.openForEdit,
        onRowAction = { row, action ->
            if (action == RowAction.PICK_DATE) {
                openActions(row, RowContext.PLANNED, true)
            } else {
                viewModel.onRowAction(row.id, action)
            }
        },
        onMore = { row -> openActions(row, RowContext.PLANNED, false) },
        onGoToInbox = onGoToInbox,
        swipeHint = swipeHint,
        modifier = modifier,
    )
}

@Composable
fun PlannedContent(
    state: PlannedUiState,
    onOpenTask: (String) -> Unit,
    onRowAction: (TaskRowUi, RowAction) -> Unit,
    onMore: (TaskRowUi) -> Unit,
    onGoToInbox: () -> Unit,
    swipeHint: SwipeHintState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        state.error?.let { error ->
            Text(
                text = error.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(Spacing.ScreenPadding),
            )
        }
        if (state.sections.isEmpty()) {
            val spec = emptyStateFor(EmptyScreen.PLANNED, hasTasks = false, filterActive = false)
            EmptyState(
                icon = Icons.Filled.CalendarMonth,
                text = stringResource(spec.textRes),
                actionLabel = stringResource(spec.actionRes),
                onAction = onGoToInbox,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
        } else {
            val firstId = state.firstTaskId()
            LazyColumn(modifier = Modifier.weight(1f)) {
                state.sections.forEach { section ->
                    item(key = "section-${section.kind}") {
                        SectionHeader(
                            text = stringResource(section.kind.titleRes()),
                            modifier = Modifier.padding(horizontal = Spacing.ScreenPadding, vertical = Spacing.InlineGap),
                        )
                    }
                    if (section.kind == PlannedSectionKind.UPCOMING) {
                        section.days.forEach { day ->
                            item(key = "day-${day.date}") {
                                Text(
                                    text = day.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = Spacing.ScreenPadding),
                                )
                            }
                            plannedRows(day.items, firstId, swipeHint, onOpenTask, onRowAction, onMore)
                        }
                    } else {
                        plannedRows(section.items, firstId, swipeHint, onOpenTask, onRowAction, onMore)
                    }
                }
            }
        }
    }
}

private fun PlannedUiState.firstTaskId(): String? = sections.firstOrNull()?.let { section ->
    section.items.firstOrNull() ?: section.days.firstOrNull()?.items?.firstOrNull()
}?.id

private fun LazyListScope.plannedRows(
    tasks: List<PlannedTaskUi>,
    firstId: String?,
    swipeHint: SwipeHintState,
    onOpen: (String) -> Unit,
    onRowAction: (TaskRowUi, RowAction) -> Unit,
    onMore: (TaskRowUi) -> Unit,
) {
    tasks.forEach { task ->
        item(key = "task-${task.id}") {
            val row = task.toRow()
            TaskRow(
                row = row,
                actions = row.actionsIn(RowContext.PLANNED),
                onAction = { onRowAction(row, it) },
                onOpen = { onOpen(row.id) },
                onMore = { onMore(row) },
                peek = swipeHint.shouldPlay() && row.id == firstId,
                onPeekDone = swipeHint::markPlayed,
            )
        }
    }
}

@StringRes
private fun PlannedSectionKind.titleRes(): Int = when (this) {
    PlannedSectionKind.OVERDUE -> R.string.planned_overdue
    PlannedSectionKind.TODAY -> R.string.planned_today
    PlannedSectionKind.TOMORROW -> R.string.planned_tomorrow
    PlannedSectionKind.UPCOMING -> R.string.planned_upcoming
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
            onRowAction = { _, _ -> },
            onMore = {},
            onGoToInbox = {},
            swipeHint = SwipeHintState(played = true),
        )
    }
}

@Preview(showBackground = true, heightDp = 480)
@Composable
private fun PlannedEmptyPreview() {
    TaskeerTheme {
        PlannedContent(
            state = PlannedUiState(),
            onOpenTask = {},
            onRowAction = { _, _ -> },
            onMore = {},
            onGoToInbox = {},
            swipeHint = SwipeHintState(played = true),
        )
    }
}
