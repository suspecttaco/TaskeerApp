package com.simposio.taskeer.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.BoardDestination
import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.ColumnDestination
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.TaskListSummary
import com.simposio.taskeer.ui.components.AppDivider
import com.simposio.taskeer.ui.components.InlineError
import com.simposio.taskeer.ui.components.SectionHeader
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import java.time.Instant

@Composable
fun DestinationPickerDialog(
    title: String,
    destinations: List<BoardDestination>,
    lists: List<TaskListSummary>,
    selectedListId: String?,
    selectedColumnId: String?,
    onSelectList: (String) -> Unit,
    onSelectColumn: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    error: DomainError? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = {
            DestinationOptions(
                destinations = destinations,
                lists = lists,
                selectedListId = selectedListId,
                selectedColumnId = selectedColumnId,
                onSelectList = onSelectList,
                onSelectColumn = onSelectColumn,
                modifier = Modifier.verticalScroll(rememberScrollState()),
                error = error,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.destination_picker_close)) }
        },
    )
}

@Composable
internal fun DestinationOptions(
    destinations: List<BoardDestination>,
    selectedColumnId: String?,
    onSelectColumn: (String) -> Unit,
    modifier: Modifier = Modifier,
    lists: List<TaskListSummary> = emptyList(),
    selectedListId: String? = null,
    onSelectList: (String) -> Unit = {},
    showBoards: Boolean = true,
    error: DomainError? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.ItemGap)) {
        error?.let { failure ->
            InlineError(
                message = failure.message,
            )
        }
        if (lists.isNotEmpty()) {
            SectionHeader(text = stringResource(R.string.destination_lists_title))
            lists.forEach { summary ->
                RadioOption(
                    label = stringResource(R.string.destination_count, summary.list.name, summary.activeCount),
                    selected = summary.list.id == selectedListId,
                    onSelect = { onSelectList(summary.list.id) },
                )
            }
        }
        if (showBoards && destinations.isEmpty()) {
            Text(
                text = stringResource(R.string.destination_no_boards),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        destinations.forEach { destination ->
            BoardDestinationSection(destination, selectedColumnId, onSelectColumn)
        }
    }
}

@Composable
private fun BoardDestinationSection(
    destination: BoardDestination,
    selectedColumnId: String?,
    onSelectColumn: (String) -> Unit,
) {
    AppDivider()
    SectionHeader(text = destination.board.name)
    if (destination.columns.isEmpty()) {
        Text(
            text = stringResource(R.string.destination_no_columns),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    destination.columns.forEach { item ->
        RadioOption(
            label = item.label(),
            selected = item.column.id == selectedColumnId,
            onSelect = { onSelectColumn(item.column.id) },
            enabled = !item.isFull,
        )
    }
}

@Composable
private fun ColumnDestination.label(): String {
    val limit = column.wipLimit
    return if (limit != null) {
        stringResource(R.string.destination_wip, column.name, activeCount, limit)
    } else {
        stringResource(R.string.destination_count, column.name, activeCount)
    }
}

@Preview(showBackground = true)
@Composable
private fun DestinationPickerDialogPreview() {
    val sync = SyncMetadata("x", Instant.EPOCH, pending = false, deletedAt = null)
    val board = Board("Proyecto", Instant.EPOCH, sync.copy(clientId = "b1"))
    val todo = Column("b1", "Por hacer", 0, wipLimit = 3, isDoneColumn = false, createdAt = Instant.EPOCH, sync = sync.copy(clientId = "c1"))
    TaskeerTheme {
        DestinationPickerDialog(
            title = "Elige destino",
            destinations = listOf(BoardDestination(board, listOf(ColumnDestination(todo, activeCount = 3)))),
            lists = emptyList(),
            selectedListId = null,
            selectedColumnId = null,
            onSelectList = {},
            onSelectColumn = {},
            onDismiss = {},
        )
    }
}
