package com.simposio.taskeer.ui.columns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.Column as BoardColumn
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.ui.tasks.TaskEditorDialog
import com.simposio.taskeer.ui.tasks.TaskEditorViewModel
import com.simposio.taskeer.ui.tasks.TasksViewModel
import com.simposio.taskeer.ui.theme.TaskeerTheme
import java.time.Instant

@Composable
fun ColumnsScreen(viewModelFactory: ViewModelProvider.Factory, modifier: Modifier = Modifier) {
    val viewModel: ColumnsViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val tasksViewModel: TasksViewModel = viewModel(factory = viewModelFactory)
    val tasks by tasksViewModel.tasks.collectAsStateWithLifecycle()

    val editorViewModel: TaskEditorViewModel = viewModel(factory = viewModelFactory)
    val editorState by editorViewModel.uiState.collectAsStateWithLifecycle()

    ColumnsContent(
        state = state,
        tasks = tasks,
        onCreate = viewModel::createColumn,
        onRename = viewModel::renameColumn,
        onMove = viewModel::moveColumn,
        onDelete = viewModel::deleteColumn,
        onCreateTask = editorViewModel::openForCreate,
        onEditTask = editorViewModel::openForEdit,
        modifier = modifier,
    )

    TaskEditorDialog(
        state = editorState,
        onTitleChange = editorViewModel::onTitleChange,
        onDescriptionChange = editorViewModel::onDescriptionChange,
        onPriorityChange = editorViewModel::onPriorityChange,
        onDueAtChange = editorViewModel::onDueAtChange,
        onAssigneeChange = editorViewModel::onAssigneeChange,
        onSave = editorViewModel::save,
        onDelete = editorViewModel::delete,
        onDismiss = editorViewModel::dismiss,
    )
}

@Composable
fun ColumnsContent(
    state: ColumnsUiState,
    tasks: List<Task>,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onMove: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
    onCreateTask: (String) -> Unit,
    onEditTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newName by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingName by rememberSaveable { mutableStateOf("") }

    val tasksByColumn = tasks.groupBy { it.columnId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(R.string.columns_title), style = MaterialTheme.typography.titleLarge)

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(stringResource(R.string.column_name_label)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    onCreate(newName)
                    newName = ""
                },
                enabled = newName.isNotBlank(),
            ) {
                Text(stringResource(R.string.column_create))
            }
        }

        state.error?.let { error ->
            Text(
                text = error.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (state.columns.isEmpty()) {
            Text(text = stringResource(R.string.columns_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(state.columns, key = { _, column -> column.id }) { index, column ->
                    ColumnRow(
                        column = column,
                        position = index,
                        lastPosition = state.columns.lastIndex,
                        tasks = tasksByColumn[column.id].orEmpty(),
                        editing = editingId == column.id,
                        editingName = editingName,
                        onEditingNameChange = { editingName = it },
                        onStartEditing = {
                            editingId = column.id
                            editingName = column.name
                        },
                        onCancelEditing = { editingId = null },
                        onConfirmRename = {
                            onRename(column.id, editingName)
                            editingId = null
                        },
                        onMoveUp = { onMove(column.id, index - 1) },
                        onMoveDown = { onMove(column.id, index + 1) },
                        onDelete = { onDelete(column.id) },
                        onCreateTask = { onCreateTask(column.id) },
                        onEditTask = onEditTask,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnRow(
    column: BoardColumn,
    position: Int,
    lastPosition: Int,
    tasks: List<Task>,
    editing: Boolean,
    editingName: String,
    onEditingNameChange: (String) -> Unit,
    onStartEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    onConfirmRename: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onCreateTask: () -> Unit,
    onEditTask: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = editingName,
                    onValueChange = onEditingNameChange,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onConfirmRename, enabled = editingName.isNotBlank()) {
                    Text(stringResource(R.string.column_save))
                }
                TextButton(onClick = onCancelEditing) {
                    Text(stringResource(R.string.column_cancel))
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = column.name, modifier = Modifier.weight(1f))
                TextButton(onClick = onMoveUp, enabled = position > 0) {
                    Text(stringResource(R.string.column_move_up))
                }
                TextButton(onClick = onMoveDown, enabled = position < lastPosition) {
                    Text(stringResource(R.string.column_move_down))
                }
                TextButton(onClick = onStartEditing) {
                    Text(stringResource(R.string.column_rename))
                }
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.column_delete))
                }
            }
        }

        tasks.forEach { task ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = task.title, modifier = Modifier.weight(1f))
                TextButton(onClick = { onEditTask(task.id) }) {
                    Text(stringResource(R.string.task_edit))
                }
            }
        }

        TextButton(onClick = onCreateTask) {
            Text(stringResource(R.string.task_add))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ColumnsContentPreview() {
    TaskeerTheme {
        ColumnsContent(
            state = ColumnsUiState(columns = previewColumns()),
            tasks = previewTasks(),
            onCreate = {},
            onRename = { _, _ -> },
            onMove = { _, _ -> },
            onDelete = {},
            onCreateTask = {},
            onEditTask = {},
        )
    }
}

private fun previewColumns(): List<BoardColumn> = listOf(
    previewColumn("Por hacer", position = 0),
    previewColumn("En progreso", position = 1),
    previewColumn("Hecho", position = 2, isDoneColumn = true),
)

private fun previewColumn(name: String, position: Int, isDoneColumn: Boolean = false): BoardColumn = BoardColumn(
    boardId = "tablero-1",
    name = name,
    position = position,
    wipLimit = null,
    isDoneColumn = isDoneColumn,
    sync = SyncMetadata(
        clientId = "columna-$position",
        updatedAt = Instant.EPOCH,
        pending = false,
        deletedAt = null,
    ),
)

private fun previewTasks(): List<Task> = listOf(
    Task(
        columnId = "columna-0",
        title = "Escribir el informe",
        description = null,
        priority = TaskPriority.MEDIUM,
        dueAt = null,
        assigneeId = null,
        position = 0,
        sync = SyncMetadata(
            clientId = "tarea-0",
            updatedAt = Instant.EPOCH,
            pending = false,
            deletedAt = null,
        ),
    ),
)
