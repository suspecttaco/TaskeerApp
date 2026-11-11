package com.simposio.taskeer.ui.inbox

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Flag
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
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.IconLabel
import com.simposio.taskeer.ui.components.MyDayToggle
import com.simposio.taskeer.ui.tasks.rememberAssignToBoardHost
import com.simposio.taskeer.ui.tasks.rememberTaskEditorHost
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
fun InboxScreen(viewModelFactory: ViewModelProvider.Factory, modifier: Modifier = Modifier) {
    val viewModel: InboxViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editorHost = rememberTaskEditorHost(viewModelFactory)
    val openAssign = rememberAssignToBoardHost(viewModelFactory)
    InboxContent(
        state = state,
        onOpenTask = editorHost.openForEdit,
        onToggleMarked = viewModel::toggleMarked,
        onAssign = openAssign,
        modifier = modifier,
    )
}

@Composable
fun InboxContent(
    state: InboxUiState,
    onOpenTask: (String) -> Unit,
    onToggleMarked: (String) -> Unit,
    onAssign: (String) -> Unit,
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
        if (state.items.isEmpty()) {
            Text(text = stringResource(R.string.inbox_empty), style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap)) {
            items(state.items, key = { it.id }) { task ->
                InboxTaskCard(task, onOpenTask, onToggleMarked, onAssign)
            }
        }
    }
}

@Composable
private fun InboxTaskCard(
    task: InboxTaskUi,
    onOpen: (String) -> Unit,
    onToggleMarked: (String) -> Unit,
    onAssign: (String) -> Unit,
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
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                ActionIcon(
                    icon = Icons.AutoMirrored.Filled.DriveFileMove,
                    contentDescription = stringResource(R.string.assign_description),
                    onClick = { onAssign(task.id) },
                )
                MyDayToggle(inMyDay = task.isMarked, onToggle = { onToggleMarked(task.id) })
            }
            IconLabel(
                icon = Icons.Filled.Flag,
                text = stringResource(task.priority.labelRes()),
                color = task.priority.color(),
                style = MaterialTheme.typography.labelMedium,
            )
            task.due?.let { due ->
                if (task.isOverdue) {
                    IconLabel(
                        icon = Icons.Filled.Warning,
                        text = due,
                        color = MaterialTheme.colorScheme.error,
                        contentDescription = stringResource(R.string.task_overdue_description, due),
                    )
                } else {
                    IconLabel(icon = Icons.Filled.Schedule, text = due)
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 480)
@Composable
private fun InboxContentPreview() {
    TaskeerTheme {
        InboxContent(
            state = InboxUiState(
                items = listOf(
                    InboxTaskUi("t1", "Comprar pan", TaskPriority.MEDIUM, null, isOverdue = false, isMarked = true),
                    InboxTaskUi("t2", "Llamar al banco", TaskPriority.HIGH, "1 oct 2026, 9:00", isOverdue = true, isMarked = false),
                ),
            ),
            onOpenTask = {},
            onToggleMarked = {},
            onAssign = {},
        )
    }
}
