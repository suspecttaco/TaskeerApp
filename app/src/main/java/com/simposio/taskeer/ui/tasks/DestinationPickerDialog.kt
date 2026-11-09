package com.simposio.taskeer.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.simposio.taskeer.ui.components.AppDivider
import com.simposio.taskeer.ui.components.SectionHeader
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import java.time.Instant

@Composable
fun DestinationPickerDialog(
    title: String,
    destinations: List<BoardDestination>,
    allowInbox: Boolean,
    selectedColumnId: String?,
    onSelectInbox: () -> Unit,
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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.ItemGap)) {
                error?.let { failure ->
                    item(key = "error") {
                        Text(
                            text = failure.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (allowInbox) {
                    item(key = "inbox") {
                        RadioOption(
                            label = stringResource(R.string.destination_inbox),
                            selected = selectedColumnId == null,
                            onSelect = onSelectInbox,
                        )
                    }
                }
                if (destinations.isEmpty()) {
                    item(key = "no-boards") {
                        Text(
                            text = stringResource(R.string.destination_no_boards),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                items(destinations, key = { it.board.id }) { destination ->
                    BoardDestinationSection(destination, selectedColumnId, onSelectColumn)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.destination_picker_close)) }
        },
    )
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
            allowInbox = true,
            selectedColumnId = null,
            onSelectInbox = {},
            onSelectColumn = {},
            onDismiss = {},
        )
    }
}
