package com.simposio.taskeer.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.TaskList
import com.simposio.taskeer.domain.model.TaskListSummary
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import java.time.Instant

class TaskListSelectorActions(
    val onSelect: (String) -> Unit,
    val onCreate: () -> Unit,
    val onRename: (String) -> Unit,
    val onDelete: (String) -> Unit,
)

@Composable
fun TaskListSelector(
    state: TaskListsUiState,
    actions: TaskListSelectorActions,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.selected
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.ScreenPadding)) {
        TextButton(onClick = { expanded = true }) {
            Text(
                text = selected?.let { stringResource(R.string.list_selector_label, it.list.name, it.activeCount) }
                    ?: stringResource(R.string.inbox_title),
                style = MaterialTheme.typography.titleMedium,
            )
            DecorativeIcon(Icons.Filled.ArrowDropDown)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.lists.forEach { summary ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.list_selector_label, summary.list.name, summary.activeCount)) },
                    onClick = {
                        expanded = false
                        actions.onSelect(summary.list.id)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.list_new)) },
                leadingIcon = { DecorativeIcon(Icons.Filled.Add) },
                onClick = {
                    expanded = false
                    actions.onCreate()
                },
            )
            val selectedId = state.selectedListId
            if (selectedId != null && state.canRenameSelected) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.list_rename)) },
                    leadingIcon = { DecorativeIcon(Icons.Filled.Edit) },
                    onClick = {
                        expanded = false
                        actions.onRename(selectedId)
                    },
                )
            }
            if (selectedId != null && state.canDeleteSelected) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.list_delete)) },
                    leadingIcon = { DecorativeIcon(Icons.Filled.Delete) },
                    onClick = {
                        expanded = false
                        actions.onDelete(selectedId)
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TaskListSelectorPreview() {
    fun summary(id: String, name: String, count: Int) = TaskListSummary(
        TaskList(name, 0, Instant.EPOCH, SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = null)),
        count,
    )
    TaskeerTheme {
        Row(horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
            TaskListSelector(
                state = TaskListsUiState(
                    lists = listOf(summary("tareas", "Tareas", 3), summary("compras", "Compras", 2)),
                ),
                actions = TaskListSelectorActions({}, {}, {}, {}),
            )
        }
    }
}
