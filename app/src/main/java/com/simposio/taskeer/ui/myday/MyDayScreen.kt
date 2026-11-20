package com.simposio.taskeer.ui.myday

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.myday.MyDaySort
import com.simposio.taskeer.ui.components.EmptyState
import com.simposio.taskeer.ui.components.IconLabel
import com.simposio.taskeer.ui.components.RowAction
import com.simposio.taskeer.ui.components.RowContext
import com.simposio.taskeer.ui.components.rowItemModifier
import com.simposio.taskeer.ui.components.TaskRow
import com.simposio.taskeer.ui.components.TaskRowUi
import com.simposio.taskeer.ui.components.actionsIn
import com.simposio.taskeer.ui.tasks.EmptyAction
import com.simposio.taskeer.ui.tasks.EmptyScreen
import com.simposio.taskeer.ui.tasks.SwipeHintState
import com.simposio.taskeer.ui.tasks.emptyStateFor
import com.simposio.taskeer.ui.tasks.firstPeekableId
import com.simposio.taskeer.ui.tasks.rememberAssignToBoardHost
import com.simposio.taskeer.ui.tasks.rememberTaskActionsSheet
import com.simposio.taskeer.ui.tasks.rememberTaskEditorHost
import com.simposio.taskeer.ui.tasks.toRow
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
fun MyDayScreen(
    viewModelFactory: ViewModelProvider.Factory,
    swipeHint: SwipeHintState,
    onAddTask: () -> Unit,
    onDelete: (String) -> Unit,
    suggestionCount: Int,
    onOpenSuggestions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MyDayViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editorHost = rememberTaskEditorHost(viewModelFactory, onDelete)
    val openAssign = rememberAssignToBoardHost(viewModelFactory)
    val handle: (String, RowAction) -> Unit = { id, action ->
        when (action) {
            RowAction.ASSIGN_TO_BOARD -> openAssign(id)
            RowAction.DELETE -> onDelete(id)
            else -> viewModel.onRowAction(id, action)
        }
    }
    val openActions = rememberTaskActionsSheet(
        onAction = { _, id, action -> handle(id, action) },
        onPickDate = { _, id, date -> viewModel.rescheduleTo(id, date) },
    )
    MyDayContent(
        state = state,
        suggestionCount = suggestionCount,
        onSort = viewModel::setSort,
        onOpenTask = editorHost.openForEdit,
        onRowAction = { row, action ->
            if (action == RowAction.PICK_DATE) {
                openActions(row, RowContext.MY_DAY, true)
            } else {
                handle(row.id, action)
            }
        },
        onMore = { row -> openActions(row, RowContext.MY_DAY, false) },
        onOpenSuggestions = onOpenSuggestions,
        onAddTask = onAddTask,
        swipeHint = swipeHint,
        modifier = modifier,
    )
}

@Composable
fun MyDayContent(
    state: MyDayUiState,
    suggestionCount: Int,
    onSort: (MyDaySort) -> Unit,
    onOpenTask: (String) -> Unit,
    onRowAction: (TaskRowUi, RowAction) -> Unit,
    onMore: (TaskRowUi) -> Unit,
    onOpenSuggestions: () -> Unit,
    onAddTask: () -> Unit,
    swipeHint: SwipeHintState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        MyDayHeader(state = state, onSort = onSort)
        // Una lista que empezó vacía no reproduce el asomo en su primera fila; antes de cargar, items vacío no cuenta.
        val startedEmpty = remember(state.loaded) { state.items.isEmpty() }
        val firstRowKey = firstPeekableId(state.items.map { it.toRow() }, RowContext.MY_DAY)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Spacing.ChromeClearance),
        ) {
            items(state.items, key = { it.id }) { task ->
                val row = task.toRow()
                TaskRow(
                    row = row,
                    actions = row.actionsIn(RowContext.MY_DAY),
                    onAction = { onRowAction(row, it) },
                    onOpen = { onOpenTask(row.id) },
                    onMore = { onMore(row) },
                    modifier = rowItemModifier(fadeIn = false),
                    peek = !startedEmpty && swipeHint.shouldPlay() && firstRowKey == row.id,
                    onPeekDone = swipeHint::markPlayed,
                )
            }
            if (state.loaded && state.items.isEmpty()) {
                item(key = EMPTY_KEY) {
                    val spec = emptyStateFor(screen = EmptyScreen.MY_DAY, suggestionCount = suggestionCount)
                    EmptyState(
                        icon = Icons.Filled.WbSunny,
                        text = stringResource(spec.textRes),
                        actionLabel = spec.actionCount
                            ?.let { stringResource(spec.actionRes, it) }
                            ?: stringResource(spec.actionRes),
                        onAction = {
                            when (spec.action) {
                                EmptyAction.SHOW_SUGGESTIONS -> onOpenSuggestions()
                                EmptyAction.ADD_TASK -> onAddTask()
                            }
                        },
                        modifier = Modifier.fillParentMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MyDayHeader(state: MyDayUiState, onSort: (MyDaySort) -> Unit) {
    Column(
        modifier = Modifier.padding(Spacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
    ) {
        val overdueText = pluralStringResource(
            R.plurals.my_day_overdue_count,
            state.overdueCount,
            state.overdueCount,
        )
        if (state.overdueCount > 0) {
            IconLabel(
                icon = Icons.Filled.Warning,
                text = overdueText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium,
            )
        } else {
            Text(
                text = overdueText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.error?.let { error ->
            Text(
                text = error.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        MyDaySortMenu(sort = state.sort, onSort = onSort)
    }
}

private const val EMPTY_KEY = "empty"

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun MyDayContentPreview() {
    TaskeerTheme {
        MyDayContent(
            state = MyDayUiState(
                items = listOf(
                    MyDayTaskUi(
                        "t2", "Entregar la memoria", TaskPriority.URGENT, "Proyecto",
                        "1 oct 2026, 9:00", isOverdue = true, isMarked = true,
                    ),
                    MyDayTaskUi(
                        "t1", "Escribir el informe", TaskPriority.HIGH, "Proyecto",
                        null, isOverdue = false, isMarked = true,
                    ),
                    MyDayTaskUi(
                        "t3", "Comprar pan", TaskPriority.MEDIUM, null,
                        null, isOverdue = false, isMarked = true, isCompleted = true,
                    ),
                ),
                overdueCount = 1,
                totalCount = 3,
                completedCount = 1,
            ),
            suggestionCount = 4,
            onSort = {},
            onOpenTask = {},
            onRowAction = { _, _ -> },
            onMore = {},
            onOpenSuggestions = {},
            onAddTask = {},
            swipeHint = SwipeHintState(played = true),
        )
    }
}

@Preview(showBackground = true, heightDp = 480)
@Composable
private fun MyDayEmptyPreview() {
    TaskeerTheme {
        MyDayContent(
            state = MyDayUiState(),
            suggestionCount = 12,
            onSort = {},
            onOpenTask = {},
            onRowAction = { _, _ -> },
            onMore = {},
            onOpenSuggestions = {},
            onAddTask = {},
            swipeHint = SwipeHintState(played = true),
        )
    }
}
