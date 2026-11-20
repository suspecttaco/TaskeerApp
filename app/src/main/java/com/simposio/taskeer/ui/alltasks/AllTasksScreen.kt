package com.simposio.taskeer.ui.alltasks

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
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
import com.simposio.taskeer.domain.planned.PlannedGroup
import com.simposio.taskeer.ui.components.DateGroupHeader
import com.simposio.taskeer.ui.components.EmptyState
import com.simposio.taskeer.ui.components.RowAction
import com.simposio.taskeer.ui.components.RowContext
import com.simposio.taskeer.ui.components.rowItemModifier
import com.simposio.taskeer.ui.components.TaskRow
import com.simposio.taskeer.ui.components.TaskRowUi
import com.simposio.taskeer.ui.components.actionsIn
import com.simposio.taskeer.ui.tasks.EmptyScreen
import com.simposio.taskeer.ui.tasks.SwipeHintState
import com.simposio.taskeer.ui.tasks.emptyStateFor
import com.simposio.taskeer.ui.tasks.firstPeekableId
import com.simposio.taskeer.ui.tasks.rememberTaskActionsSheet
import com.simposio.taskeer.ui.tasks.rememberTaskEditorHost
import com.simposio.taskeer.ui.tasks.toRow
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import com.simposio.taskeer.ui.upcoming.PlannedTaskUi
import java.time.LocalDate

@Composable
fun AllTasksScreen(
    viewModelFactory: ViewModelProvider.Factory,
    swipeHint: SwipeHintState,
    onAddForDate: (LocalDate?) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AllTasksViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editorHost = rememberTaskEditorHost(viewModelFactory, onDelete)
    val openActions = rememberTaskActionsSheet(
        onAction = { _, id, action ->
            if (action == RowAction.DELETE) onDelete(id) else viewModel.onRowAction(id, action)
        },
        onPickDate = { _, id, date -> viewModel.rescheduleTo(id, date) },
    )
    AllTasksContent(
        state = state,
        onOpenTask = editorHost.openForEdit,
        onRowAction = { row, action ->
            when (action) {
                RowAction.PICK_DATE -> openActions(row, RowContext.PLANNED, true)
                RowAction.DELETE -> onDelete(row.id)
                else -> viewModel.onRowAction(row.id, action)
            }
        },
        onMore = { row -> openActions(row, RowContext.PLANNED, false) },
        onAddToGroup = { group -> onAddForDate(viewModel.addDateFor(group)) },
        onAddWithoutDate = { onAddForDate(null) },
        swipeHint = swipeHint,
        modifier = modifier,
    )
}

@Composable
fun AllTasksContent(
    state: AllTasksUiState,
    onOpenTask: (String) -> Unit,
    onRowAction: (TaskRowUi, RowAction) -> Unit,
    onMore: (TaskRowUi) -> Unit,
    onAddToGroup: (PlannedGroup) -> Unit,
    onAddWithoutDate: () -> Unit,
    swipeHint: SwipeHintState,
    modifier: Modifier = Modifier,
) {
    val firstId = firstPeekableId(
        state.groups.flatMap { group -> group.items.map { it.toRow() } },
        RowContext.PLANNED,
    )
    val titles = state.groups.map { stringResource(it.group.titleRes()) }
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
            state.groups.forEachIndexed { index, group ->
                item(key = "group-${group.group}") {
                    DateGroupHeader(
                        title = titles[index],
                        addDescription = addDescriptions[index],
                        onAdd = { onAddToGroup(group.group) },
                        count = group.count,
                    )
                }
                items(group.items, key = { "task-${it.id}" }) { task ->
                    val row = task.toRow()
                    TaskRow(
                        row = row,
                        actions = row.actionsIn(RowContext.PLANNED),
                        onAction = { onRowAction(row, it) },
                        onOpen = { onOpenTask(row.id) },
                        onMore = { onMore(row) },
                        modifier = rowItemModifier(),
                        peek = swipeHint.shouldPlay() && row.id == firstId,
                        onPeekDone = swipeHint::markPlayed,
                    )
                }
            }
            if (state.isEmpty) {
                item(key = "empty") {
                    val spec = emptyStateFor(EmptyScreen.ALL_TASKS, hasTasks = false, filterActive = false)
                    EmptyState(
                        icon = Icons.Filled.Checklist,
                        text = stringResource(spec.textRes),
                        actionLabel = stringResource(spec.actionRes),
                        onAction = { onAddWithoutDate() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@StringRes
private fun PlannedGroup.titleRes(): Int = when (this) {
    PlannedGroup.TODAY -> R.string.group_today
    PlannedGroup.TOMORROW -> R.string.group_tomorrow
    PlannedGroup.UPCOMING -> R.string.group_upcoming
    PlannedGroup.SOMEDAY -> R.string.group_someday
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun AllTasksContentPreview() {
    TaskeerTheme {
        AllTasksContent(
            state = AllTasksUiState(
                groups = listOf(
                    AllTasksGroupUi(
                        PlannedGroup.TODAY,
                        listOf(PlannedTaskUi("t1", "Entregar la memoria", TaskPriority.URGENT, "Proyecto", "9:00", true)),
                    ),
                    AllTasksGroupUi(PlannedGroup.TOMORROW, emptyList()),
                    AllTasksGroupUi(PlannedGroup.UPCOMING, emptyList()),
                    AllTasksGroupUi(PlannedGroup.SOMEDAY, emptyList()),
                ),
                isEmpty = false,
            ),
            onOpenTask = {},
            onRowAction = { _, _ -> },
            onMore = {},
            onAddToGroup = {},
            onAddWithoutDate = {},
            swipeHint = SwipeHintState(played = true),
        )
    }
}
