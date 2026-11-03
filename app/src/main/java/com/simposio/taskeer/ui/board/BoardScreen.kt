package com.simposio.taskeer.ui.board

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.Column as BoardColumn
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.ui.tasks.DUE_DATE_TIME_FORMATTER
import com.simposio.taskeer.ui.tasks.SubtaskActions
import com.simposio.taskeer.ui.tasks.SubtasksViewModel
import com.simposio.taskeer.ui.tasks.TaskEditorDialog
import com.simposio.taskeer.ui.tasks.TaskEditorViewModel
import com.simposio.taskeer.ui.theme.TaskeerTheme
import java.time.Instant
import java.time.LocalDateTime
import kotlin.math.roundToInt

private val ColumnWidth = 280.dp

@Composable
fun BoardScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onManageColumns: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BoardViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val editorViewModel: TaskEditorViewModel = viewModel(factory = viewModelFactory)
    val editorState by editorViewModel.uiState.collectAsStateWithLifecycle()

    val subtasksViewModel: SubtasksViewModel = viewModel(factory = viewModelFactory)
    val subtasksState by subtasksViewModel.uiState.collectAsStateWithLifecycle()

    val openTaskId = editorState.taskId.takeIf { editorState.isOpen }
    LaunchedEffect(openTaskId) {
        if (openTaskId == null) {
            subtasksViewModel.close()
        } else {
            subtasksViewModel.open(openTaskId)
        }
    }

    BoardContent(
        state = state,
        onMove = viewModel::moveTask,
        onCreateTask = editorViewModel::openForCreate,
        onEditTask = editorViewModel::openForEdit,
        onManageColumns = { onManageColumns(viewModel.boardId) },
        onAddColumn = viewModel::openNewColumn,
        onNewColumnNameChange = viewModel::onNewColumnNameChange,
        onSaveNewColumn = viewModel::saveNewColumn,
        onDismissNewColumn = viewModel::dismissNewColumn,
        modifier = modifier,
    )

    TaskEditorDialog(
        state = editorState,
        onTitleChange = editorViewModel::onTitleChange,
        onDescriptionChange = editorViewModel::onDescriptionChange,
        onPriorityChange = editorViewModel::onPriorityChange,
        onDueAtChange = editorViewModel::onDueAtChange,
        onAssigneeChange = editorViewModel::onAssigneeChange,
        subtasks = subtasksState,
        subtaskActions = SubtaskActions(
            onNewTitleChange = subtasksViewModel::onNewTitleChange,
            onAdd = subtasksViewModel::add,
            onToggle = subtasksViewModel::toggle,
            onStartEdit = subtasksViewModel::startEdit,
            onEditTitleChange = subtasksViewModel::onEditTitleChange,
            onEditDueAtChange = subtasksViewModel::onEditDueAtChange,
            onEditAssigneeChange = subtasksViewModel::onEditAssigneeChange,
            onSaveEdit = subtasksViewModel::saveEdit,
            onCancelEdit = subtasksViewModel::cancelEdit,
            onDelete = subtasksViewModel::delete,
        ),
        onSave = editorViewModel::save,
        onDelete = editorViewModel::delete,
        onDismiss = editorViewModel::dismiss,
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
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onAddColumn) {
                    Text(stringResource(R.string.board_add_column))
                }
                TextButton(onClick = onManageColumns) {
                    Text(stringResource(R.string.board_manage_columns))
                }
            }

            state.error?.let { error ->
                Text(
                    text = error.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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

@Composable
private fun BoardColumnView(
    column: BoardColumnUi,
    draggedId: String?,
    onColumnPositioned: (Bounds) -> Unit,
    onColumnDisposed: () -> Unit,
    onCardPositioned: (String, Bounds) -> Unit,
    onCardDisposed: (String) -> Unit,
    onDragStart: (TaskCardUi, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onCreateTask: () -> Unit,
    onEditTask: (String) -> Unit,
) {
    DisposableEffect(column.id) { onDispose(onColumnDisposed) }

    Card(
        modifier = Modifier
            .width(ColumnWidth)
            .fillMaxHeight()
            .onGloballyPositioned { onColumnPositioned(it.boundsInRoot().toBounds()) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = column.column.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (column.cards.isEmpty()) {
                    Text(
                        text = stringResource(R.string.board_column_empty),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                column.cards.forEach { card ->
                    DisposableEffect(card.id) { onDispose { onCardDisposed(card.id) } }
                    TaskCard(
                        card = card,
                        modifier = Modifier
                            .alpha(if (card.id == draggedId) 0.3f else 1f)
                            .onGloballyPositioned { onCardPositioned(card.id, it.boundsInRoot().toBounds()) }
                            .pointerInput(card.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { onDragStart(card, it) },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        onDrag(amount)
                                    },
                                    onDragEnd = onDragEnd,
                                    onDragCancel = onDragCancel,
                                )
                            }
                            .clickable { onEditTask(card.id) },
                    )
                }
            }
            TextButton(onClick = onCreateTask) {
                Text(stringResource(R.string.task_add))
            }
        }
    }
}

@Composable
private fun TaskCard(card: TaskCardUi, modifier: Modifier = Modifier, elevated: Boolean = false) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (elevated) 8.dp else 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (card.inMyDay) {
                    val description = stringResource(R.string.task_in_my_day)
                    Text(
                        text = "★",
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.semantics { contentDescription = description },
                    )
                }
            }
            Text(
                text = stringResource(card.priority.labelRes()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            card.dueAtLocal?.let {
                Text(text = it.format(DUE_DATE_TIME_FORMATTER), style = MaterialTheme.typography.bodySmall)
            }
            card.assigneeId?.let { id ->
                val label = if (card.assigneeIsMe) stringResource(R.string.task_assignee_me) else id
                Text(text = label, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun androidx.compose.ui.geometry.Rect.toBounds(): Bounds = Bounds(left, top, right, bottom)

@StringRes
private fun TaskPriority.labelRes(): Int = when (this) {
    TaskPriority.LOW -> R.string.task_priority_low
    TaskPriority.MEDIUM -> R.string.task_priority_medium
    TaskPriority.HIGH -> R.string.task_priority_high
    TaskPriority.URGENT -> R.string.task_priority_urgent
}

@Preview(showBackground = true)
@Composable
private fun TaskCardPreview() {
    TaskeerTheme {
        TaskCard(
            card = TaskCardUi(
                id = "t1",
                title = "Escribir el informe",
                priority = TaskPriority.HIGH,
                dueAt = Instant.EPOCH,
                dueAtLocal = LocalDateTime.of(2026, 10, 22, 12, 30),
                assigneeId = "local-user",
                assigneeIsMe = true,
                inMyDay = true,
            ),
        )
    }
}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun BoardContentPreview() {
    TaskeerTheme {
        BoardContent(
            state = BoardUiState(
                columns = listOf(
                    BoardColumnUi(previewColumn("Por hacer", 0), emptyList()),
                    BoardColumnUi(previewColumn("En progreso", 1), emptyList()),
                ),
            ),
            onMove = { _, _, _ -> },
            onCreateTask = {},
            onEditTask = {},
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

private fun previewColumn(name: String, position: Int): BoardColumn = BoardColumn(
    boardId = "tablero-1",
    name = name,
    position = position,
    wipLimit = null,
    isDoneColumn = false,
    createdAt = Instant.EPOCH,
    sync = SyncMetadata("columna-$position", Instant.EPOCH, pending = false, deletedAt = null),
)
