package com.simposio.taskeer.ui.myday

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.myday.MyDayFilter
import com.simposio.taskeer.domain.myday.MyDaySort
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.EmptyState
import com.simposio.taskeer.ui.components.IconLabel
import com.simposio.taskeer.ui.components.RowAction
import com.simposio.taskeer.ui.components.RowContext
import com.simposio.taskeer.ui.components.SectionHeader
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
    modifier: Modifier = Modifier,
) {
    val viewModel: MyDayViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editorHost = rememberTaskEditorHost(viewModelFactory)
    val openAssign = rememberAssignToBoardHost(viewModelFactory)
    val handle: (RowContext, String, RowAction) -> Unit = { context, id, action ->
        when (action) {
            RowAction.ASSIGN_TO_BOARD -> openAssign(id)
            RowAction.DELETE -> onDelete(id)
            else -> viewModel.onRowAction(context, id, action)
        }
    }
    val openActions = rememberTaskActionsSheet(
        onAction = handle,
        onPickDate = { _, id, date -> viewModel.rescheduleTo(id, date) },
    )
    MyDayContent(
        state = state,
        onFilter = viewModel::setFilter,
        onSort = viewModel::setSort,
        onOpenTask = editorHost.openForEdit,
        onRowAction = { context, row, action ->
            if (action == RowAction.PICK_DATE) openActions(row, context, true) else handle(context, row.id, action)
        },
        onMore = { row, context -> openActions(row, context, false) },
        onToggleSuggestions = viewModel::toggleSuggestions,
        onAddTask = onAddTask,
        swipeHint = swipeHint,
        modifier = modifier,
    )
}

@Composable
fun MyDayContent(
    state: MyDayUiState,
    onFilter: (MyDayFilter) -> Unit,
    onSort: (MyDaySort) -> Unit,
    onOpenTask: (String) -> Unit,
    onRowAction: (RowContext, TaskRowUi, RowAction) -> Unit,
    onMore: (TaskRowUi, RowContext) -> Unit,
    onToggleSuggestions: () -> Unit,
    onAddTask: () -> Unit,
    swipeHint: SwipeHintState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        MyDayHeader(state = state, onFilter = onFilter, onSort = onSort)
        val hasSuggestions = state.suggestions.isNotEmpty()
        val firstRowKey = when {
            hasSuggestions && state.suggestionsExpanded -> SUGGESTION_KEY_PREFIX + state.suggestions.first().id
            else -> firstPeekableId(state.items.map { it.toRow() }, RowContext.MY_DAY)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Spacing.ChromeClearance),
        ) {
            if (hasSuggestions) {
                item(key = SUGGESTIONS_HEADER_KEY) {
                    SuggestionsHeader(
                        count = state.suggestions.size,
                        expanded = state.suggestionsExpanded,
                        onToggle = onToggleSuggestions,
                    )
                }
                if (state.suggestionsExpanded) {
                    items(state.suggestions, key = { SUGGESTION_KEY_PREFIX + it.id }) { suggestion ->
                        val row = suggestion.toRow()
                        TaskRow(
                            row = row,
                            actions = row.actionsIn(RowContext.SUGGESTION),
                            onAction = { onRowAction(RowContext.SUGGESTION, row, it) },
                            onOpen = { onOpenTask(row.id) },
                            onMore = { onMore(row, RowContext.SUGGESTION) },
                            primaryAction = RowAction.KEEP_TODAY,
                            modifier = rowItemModifier(),
                            peek = swipeHint.shouldPlay() && firstRowKey == SUGGESTION_KEY_PREFIX + row.id,
                            onPeekDone = swipeHint::markPlayed,
                        )
                    }
                }
            }
            items(state.items, key = { it.id }) { task ->
                val row = task.toRow()
                TaskRow(
                    row = row,
                    actions = row.actionsIn(RowContext.MY_DAY),
                    onAction = { onRowAction(RowContext.MY_DAY, row, it) },
                    onOpen = { onOpenTask(row.id) },
                    onMore = { onMore(row, RowContext.MY_DAY) },
                    modifier = rowItemModifier(),
                    peek = swipeHint.shouldPlay() && firstRowKey == row.id,
                    onPeekDone = swipeHint::markPlayed,
                )
            }
            if (state.items.isEmpty()) {
                item(key = EMPTY_KEY) {
                    val spec = emptyStateFor(
                        screen = EmptyScreen.MY_DAY,
                        hasTasks = state.totalCount > 0,
                        filterActive = state.filter != MyDayFilter.ALL,
                    )
                    EmptyState(
                        icon = Icons.Filled.WbSunny,
                        text = stringResource(spec.textRes),
                        actionLabel = stringResource(spec.actionRes),
                        onAction = {
                            when (spec.action) {
                                EmptyAction.SHOW_ALL -> onFilter(MyDayFilter.ALL)
                                else -> onAddTask()
                            }
                        },
                        modifier = if (hasSuggestions) Modifier.fillParentMaxWidth() else Modifier.fillParentMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MyDayHeader(state: MyDayUiState, onFilter: (MyDayFilter) -> Unit, onSort: (MyDaySort) -> Unit) {
    val filterDescription = stringResource(R.string.my_day_filter_label)
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
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .semantics { contentDescription = filterDescription },
            horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DecorativeIcon(icon = Icons.Filled.FilterList, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            MyDayFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { onFilter(filter) },
                    label = { Text(stringResource(filter.labelRes())) },
                )
            }
            MyDaySortMenu(sort = state.sort, onSort = onSort)
        }
    }
}

private const val SUGGESTIONS_HEADER_KEY = "suggestions-header"
private const val SUGGESTION_KEY_PREFIX = "suggestion-"
private const val EMPTY_KEY = "empty"

@Composable
private fun SuggestionsHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionHeader(
            text = stringResource(R.string.my_day_suggestions_title, count),
            modifier = Modifier.weight(1f),
        )
        ActionIcon(
            icon = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = stringResource(
                if (expanded) R.string.my_day_suggestions_collapse else R.string.my_day_suggestions_expand,
            ),
            onClick = onToggle,
        )
    }
}

@StringRes
private fun MyDayFilter.labelRes(): Int = when (this) {
    MyDayFilter.ALL -> R.string.my_day_filter_all
    MyDayFilter.MARKED -> R.string.my_day_filter_marked
    MyDayFilter.ASSIGNED -> R.string.my_day_filter_assigned
    MyDayFilter.OVERDUE -> R.string.my_day_filter_overdue
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun MyDayContentPreview() {
    TaskeerTheme {
        MyDayContent(
            state = MyDayUiState(
                items = listOf(
                    MyDayTaskUi(
                        "t2", "Entregar la memoria", TaskPriority.URGENT, "Proyecto",
                        "1 oct 2026, 9:00", isOverdue = true, isMarked = true, isAssigned = true,
                    ),
                    MyDayTaskUi(
                        "t1", "Escribir el informe", TaskPriority.HIGH, "Proyecto",
                        null, isOverdue = false, isMarked = true, isAssigned = false,
                    ),
                    MyDayTaskUi(
                        "t3", "Comprar pan", TaskPriority.MEDIUM, null,
                        null, isOverdue = false, isMarked = true, isAssigned = false, isCompleted = true,
                    ),
                ),
                overdueCount = 1,
                totalCount = 3,
                completedCount = 1,
                suggestions = listOf(MyDaySuggestionUi("s1", "Revisar el presupuesto", "Proyecto")),
            ),
            onFilter = {},
            onSort = {},
            onOpenTask = {},
            onRowAction = { _, _, _ -> },
            onMore = { _, _ -> },
            onToggleSuggestions = {},
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
            onFilter = {},
            onSort = {},
            onOpenTask = {},
            onRowAction = { _, _, _ -> },
            onMore = { _, _ -> },
            onToggleSuggestions = {},
            onAddTask = {},
            swipeHint = SwipeHintState(played = true),
        )
    }
}
