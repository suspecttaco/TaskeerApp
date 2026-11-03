package com.simposio.taskeer.ui.myday

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.myday.MyDayFilter
import com.simposio.taskeer.domain.myday.MyDaySort
import com.simposio.taskeer.ui.board.labelRes
import com.simposio.taskeer.ui.tasks.rememberTaskEditorHost
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
fun MyDayScreen(viewModelFactory: ViewModelProvider.Factory, modifier: Modifier = Modifier) {
    val viewModel: MyDayViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editorHost = rememberTaskEditorHost(viewModelFactory)
    MyDayContent(
        state = state,
        onSort = viewModel::setSort,
        onFilter = viewModel::setFilter,
        onOpenTask = editorHost.openForEdit,
        onToggleMarked = viewModel::toggleMarked,
        modifier = modifier,
    )
}

@Composable
fun MyDayContent(
    state: MyDayUiState,
    onSort: (MyDaySort) -> Unit,
    onFilter: (MyDayFilter) -> Unit,
    onOpenTask: (String) -> Unit,
    onToggleMarked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.screen_my_day),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = pluralStringResource(
                R.plurals.my_day_overdue_count,
                state.overdueCount,
                state.overdueCount,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = if (state.overdueCount > 0) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        state.error?.let { error ->
            Text(
                text = error.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        ChipRow(label = R.string.my_day_filter_label) {
            MyDayFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { onFilter(filter) },
                    label = { Text(stringResource(filter.labelRes())) },
                )
            }
        }
        ChipRow(label = R.string.my_day_sort_label) {
            MyDaySort.entries.forEach { sort ->
                FilterChip(
                    selected = state.sort == sort,
                    onClick = { onSort(sort) },
                    label = { Text(stringResource(sort.labelRes())) },
                )
            }
        }
        if (state.items.isEmpty()) {
            Text(
                text = stringResource(
                    if (state.totalCount == 0) R.string.my_day_empty else R.string.my_day_filter_empty,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.items, key = { it.id }) { task ->
                MyDayTaskCard(task = task, onOpen = onOpenTask, onToggleMarked = onToggleMarked)
            }
        }
    }
}

@Composable
private fun ChipRow(@StringRes label: Int, chips: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(label), style = MaterialTheme.typography.labelLarge)
        chips()
    }
}

@StringRes
private fun MyDayFilter.labelRes(): Int = when (this) {
    MyDayFilter.ALL -> R.string.my_day_filter_all
    MyDayFilter.MARKED -> R.string.my_day_filter_marked
    MyDayFilter.ASSIGNED -> R.string.my_day_filter_assigned
    MyDayFilter.OVERDUE -> R.string.my_day_filter_overdue
}

@StringRes
private fun MyDaySort.labelRes(): Int = when (this) {
    MyDaySort.DUE_DATE -> R.string.my_day_sort_due_date
    MyDaySort.PRIORITY -> R.string.my_day_sort_priority
    MyDaySort.MARKED_AT -> R.string.my_day_sort_marked_at
}

@Composable
private fun MyDayTaskCard(
    task: MyDayTaskUi,
    onOpen: (String) -> Unit,
    onToggleMarked: (String) -> Unit,
) {
    Card(
        onClick = { onOpen(task.id) },
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                val description = stringResource(
                    if (task.isMarked) R.string.task_in_my_day_remove else R.string.task_in_my_day_add,
                )
                Text(
                    text = if (task.isMarked) "★" else "☆",
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .clickable { onToggleMarked(task.id) }
                        .padding(4.dp)
                        .semantics { contentDescription = description },
                )
            }
            Text(
                text = stringResource(task.priority.labelRes()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(text = task.boardName, style = MaterialTheme.typography.bodySmall)
            if (task.isAssigned) {
                Text(
                    text = stringResource(R.string.my_day_assigned_tag),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            task.due?.let { due ->
                if (task.isOverdue) {
                    val overdueDescription = stringResource(R.string.task_overdue_description, due)
                    Text(
                        text = due,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { contentDescription = overdueDescription },
                    )
                } else {
                    Text(text = due, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
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
                ),
                overdueCount = 1,
                totalCount = 2,
            ),
            onSort = {},
            onFilter = {},
            onOpenTask = {},
            onToggleMarked = {},
        )
    }
}
