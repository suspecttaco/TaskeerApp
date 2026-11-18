package com.simposio.taskeer.ui.upcoming

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.planned.DayLabel
import com.simposio.taskeer.domain.planned.toggleCollapsed
import com.simposio.taskeer.ui.components.DateGroupHeader
import com.simposio.taskeer.ui.components.EmptyState
import com.simposio.taskeer.ui.components.RowAction
import com.simposio.taskeer.ui.components.RowContext
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

private val CollapsedSaver = listSaver<MutableState<Set<String>>, String>(
    save = { it.value.toList() },
    restore = { mutableStateOf(it.toSet()) },
)

@Composable
fun UpcomingScreen(
    viewModelFactory: ViewModelProvider.Factory,
    swipeHint: SwipeHintState,
    onAddForDate: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: UpcomingViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editorHost = rememberTaskEditorHost(viewModelFactory)
    val openActions = rememberTaskActionsSheet(
        onAction = { _, id, action -> viewModel.onRowAction(id, action) },
        onPickDate = { _, id, date -> viewModel.rescheduleTo(id, date) },
    )
    UpcomingContent(
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
        onAddForDate = onAddForDate,
        swipeHint = swipeHint,
        modifier = modifier,
    )
}

@Composable
fun UpcomingContent(
    state: UpcomingUiState,
    onOpenTask: (String) -> Unit,
    onRowAction: (TaskRowUi, RowAction) -> Unit,
    onMore: (TaskRowUi) -> Unit,
    onAddForDate: (LocalDate?) -> Unit,
    swipeHint: SwipeHintState,
    modifier: Modifier = Modifier,
) {
    val collapsedState = rememberSaveable(saver = CollapsedSaver) { mutableStateOf(emptySet<String>()) }
    var collapsed by collapsedState
    val firstId = state.days.firstNotNullOfOrNull { day -> day.items.firstOrNull()?.id }
    val titles = state.days.map { it.label.text() }
    val addDescriptions = titles.map { stringResource(R.string.group_add_to, it) }
    Column(modifier = modifier.fillMaxSize()) {
        state.error?.let { error ->
            Text(
                text = error.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(Spacing.ScreenPadding),
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Spacing.ChromeClearance),
        ) {
            state.days.forEachIndexed { index, day ->
                val key = day.date.toString()
                val expanded = key !in collapsed
                item(key = "day-$key") {
                    DateGroupHeader(
                        title = titles[index],
                        addDescription = addDescriptions[index],
                        onAdd = { onAddForDate(day.date) },
                        expanded = expanded,
                        onToggle = { collapsed = toggleCollapsed(collapsed, key) },
                    )
                }
                if (expanded) {
                    items(day.items, key = { "task-${it.id}" }) { task ->
                        val row = task.toRow()
                        TaskRow(
                            row = row,
                            actions = row.actionsIn(RowContext.PLANNED),
                            onAction = { onRowAction(row, it) },
                            onOpen = { onOpenTask(row.id) },
                            onMore = { onMore(row) },
                            peek = swipeHint.shouldPlay() && row.id == firstId,
                            onPeekDone = swipeHint::markPlayed,
                        )
                    }
                }
            }
            if (state.isEmpty) {
                item(key = "empty") {
                    val spec = emptyStateFor(EmptyScreen.UPCOMING, hasTasks = false, filterActive = false)
                    EmptyState(
                        icon = Icons.Filled.DateRange,
                        text = stringResource(spec.textRes),
                        actionLabel = stringResource(spec.actionRes),
                        onAction = { onAddForDate(null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun UpcomingContentPreview() {
    val today = LocalDate.of(2026, 11, 17)
    TaskeerTheme {
        UpcomingContent(
            state = UpcomingUiState(
                days = listOf(
                    UpcomingDayUi(
                        today,
                        DayLabel.Today,
                        listOf(PlannedTaskUi("t1", "Entregar la memoria", TaskPriority.URGENT, "Proyecto", "9:00", true)),
                    ),
                    UpcomingDayUi(today.plusDays(1), DayLabel.Tomorrow, emptyList()),
                    UpcomingDayUi(today.plusDays(2), DayLabel.Weekday(today.plusDays(2)), emptyList()),
                ),
                isEmpty = false,
            ),
            onOpenTask = {},
            onRowAction = { _, _ -> },
            onMore = {},
            onAddForDate = {},
            swipeHint = SwipeHintState(played = true),
        )
    }
}
