package com.simposio.taskeer.ui.board

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.Column as BoardColumn
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.tasks.rememberTaskEditorHost
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import java.time.Instant
import kotlin.math.roundToInt

internal val ColumnWidth = 280.dp

@Composable
fun BoardScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onManageColumns: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BoardViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val editorHost = rememberTaskEditorHost(viewModelFactory)

    BoardContent(
        state = state,
        onMove = viewModel::moveTask,
        onCreateTask = editorHost.openForCreate,
        onEditTask = editorHost.openForEdit,
        onToggleMyDay = viewModel::toggleMyDay,
        onManageColumns = { onManageColumns(viewModel.boardId) },
        onAddColumn = viewModel::openNewColumn,
        onNewColumnNameChange = viewModel::onNewColumnNameChange,
        onSaveNewColumn = viewModel::saveNewColumn,
        onDismissNewColumn = viewModel::dismissNewColumn,
        modifier = modifier,
    )
}

private class DragState(
    val card: TaskCardUi,
    val grabOffset: Offset,
    var pointer: Offset,
)

@Composable
fun BoardContent(
    state: BoardUiState,
    onMove: (String, String, Int) -> Unit,
    onCreateTask: (String) -> Unit,
    onEditTask: (String) -> Unit,
    onToggleMyDay: (String) -> Unit,
    onManageColumns: () -> Unit,
    onAddColumn: () -> Unit,
    onNewColumnNameChange: (String) -> Unit,
    onSaveNewColumn: () -> Unit,
    onDismissNewColumn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val columnBounds = remember { mutableStateMapOf<String, Bounds>() }
    val cardBounds = remember { mutableStateMapOf<String, CardBounds>() }
    val cardColumn = remember { mutableStateMapOf<String, String>() }
    var drag by remember { mutableStateOf<DragState?>(null) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInRoot() },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.ScreenPadding),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionIcon(
                    icon = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.board_add_column),
                    onClick = onAddColumn,
                )
                ActionIcon(
                    icon = Icons.Filled.ViewColumn,
                    contentDescription = stringResource(R.string.board_manage_columns),
                    onClick = onManageColumns,
                )
            }

            state.error?.let { error ->
                Text(
                    text = error.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Spacing.ScreenPadding),
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.ScreenPadding, vertical = Spacing.InlineGap),
                horizontalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
            ) {
                state.columns.forEach { column ->
                    BoardColumnView(
                        column = column,
                        draggedId = drag?.card?.id,
                        onColumnPositioned = { columnBounds[column.id] = it },
                        onColumnDisposed = { columnBounds.remove(column.id) },
                        onCardPositioned = { id, bounds ->
                            cardBounds[id] = CardBounds(id, bounds)
                            cardColumn[id] = column.id
                        },
                        onCardDisposed = { id ->
                            cardBounds.remove(id)
                            cardColumn.remove(id)
                        },
                        onDragStart = { card, grab ->
                            val origin = cardBounds[card.id]?.bounds
                            val start = Offset((origin?.left ?: 0f) + grab.x, (origin?.top ?: 0f) + grab.y)
                            drag = DragState(card, grab, start)
                        },
                        onDrag = { delta ->
                            drag?.let { current ->
                                drag = DragState(current.card, current.grabOffset, current.pointer + delta)
                            }
                        },
                        onDragEnd = {
                            drag?.let { current ->
                                val target = DropTargetResolver.resolve(
                                    x = current.pointer.x,
                                    y = current.pointer.y,
                                    columnBounds = columnBounds.toMap(),
                                    cardsByColumn = cardsByColumn(cardBounds, cardColumn),
                                    draggedId = current.card.id,
                                )
                                if (target != null) {
                                    onMove(current.card.id, target.columnId, target.index)
                                }
                            }
                            drag = null
                        },
                        onDragCancel = { drag = null },
                        onCreateTask = { onCreateTask(column.id) },
                        onEditTask = onEditTask,
                        onToggleMyDay = onToggleMyDay,
                    )
                }
            }
        }

        if (state.newColumn.isOpen) {
            NewColumnDialog(
                state = state.newColumn,
                onNameChange = onNewColumnNameChange,
                onSave = onSaveNewColumn,
                onDismiss = onDismissNewColumn,
            )
        }

        drag?.let { current ->
            val topLeft = current.pointer - current.grabOffset - rootOrigin
            TaskCard(
                card = current.card,
                modifier = Modifier
                    .width(ColumnWidth - 24.dp)
                    .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) },
                elevated = true,
            )
        }
    }
}

private fun cardsByColumn(
    cards: SnapshotStateMap<String, CardBounds>,
    cardColumn: SnapshotStateMap<String, String>,
): Map<String, List<CardBounds>> =
    cards.values
        .groupBy { cardColumn[it.id].orEmpty() }
        .mapValues { (_, list) -> list.sortedBy { it.bounds.top } }


internal fun androidx.compose.ui.geometry.Rect.toBounds(): Bounds = Bounds(left, top, right, bottom)

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun BoardContentPreview() {
    TaskeerTheme {
        BoardContent(
            state = BoardUiState(
                columns = listOf(
                    BoardColumnUi(previewColumn("Por hacer", 0), emptyList()),
                    BoardColumnUi(previewColumn("En progreso", 1, wipLimit = 3), emptyList()),
                    BoardColumnUi(previewColumn("Hecho", 2, isDoneColumn = true), emptyList()),
                ),
            ),
            onMove = { _, _, _ -> },
            onCreateTask = {},
            onEditTask = {},
            onToggleMyDay = {},
            onManageColumns = {},
            onAddColumn = {},
            onNewColumnNameChange = {},
            onSaveNewColumn = {},
            onDismissNewColumn = {},
        )
    }
}

@Composable
private fun NewColumnDialog(
    state: NewColumnUiState,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.board_add_column_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.column_name_label)) },
                    singleLine = true,
                    isError = state.error != null,
                )
                state.error?.let { error ->
                    Text(
                        text = error.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = state.name.isNotBlank()) {
                Text(stringResource(R.string.column_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.column_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun NewColumnDialogPreview() {
    TaskeerTheme {
        NewColumnDialog(
            state = NewColumnUiState(isOpen = true, name = "Revisión"),
            onNameChange = {},
            onSave = {},
            onDismiss = {},
        )
    }
}

private fun previewColumn(
    name: String,
    position: Int,
    wipLimit: Int? = null,
    isDoneColumn: Boolean = false,
): BoardColumn = BoardColumn(
    boardId = "tablero-1",
    name = name,
    position = position,
    wipLimit = wipLimit,
    isDoneColumn = isDoneColumn,
    createdAt = Instant.EPOCH,
    sync = SyncMetadata("columna-$position", Instant.EPOCH, pending = false, deletedAt = null),
)
