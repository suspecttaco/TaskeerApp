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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.Column as BoardColumn
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.AppDivider
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.InlineAddField
import com.simposio.taskeer.ui.tasks.TasksViewModel
import com.simposio.taskeer.ui.tasks.rememberTaskEditorHost
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import java.time.Instant

@Composable
fun ColumnsScreen(viewModelFactory: ViewModelProvider.Factory, modifier: Modifier = Modifier) {
    val viewModel: ColumnsViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val tasksViewModel: TasksViewModel = viewModel(factory = viewModelFactory)
    val tasks by tasksViewModel.tasks.collectAsStateWithLifecycle()

    val editorHost = rememberTaskEditorHost(viewModelFactory)

    ColumnsContent(
        state = state,
        tasks = tasks,
        onCreate = viewModel::createColumn,
        onRename = viewModel::renameColumn,
        onMove = viewModel::moveColumn,
        onDelete = viewModel::deleteColumn,
        onSetWipLimit = viewModel::setWipLimit,
        onCreateTask = editorHost.openForCreate,
        onEditTask = editorHost.openForEdit,
        modifier = modifier,
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
    onSetWipLimit: (String, Int?) -> Unit,
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
            .padding(Spacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
    ) {
        InlineAddField(
            value = newName,
            onValueChange = { newName = it },
            label = stringResource(R.string.column_name_label),
            addDescription = stringResource(R.string.column_create),
            onAdd = {
                onCreate(newName)
                newName = ""
            },
            enabled = newName.isNotBlank(),
        )

        state.error?.let { error ->
            Text(
                text = error.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (state.columns.isEmpty()) {
            Text(
                text = stringResource(R.string.columns_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.ItemGap),
            ) {
                itemsIndexed(state.columns, key = { _, column -> column.id }) { index, column ->
                    Column {
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
                            onSetWipLimit = { onSetWipLimit(column.id, it) },
                            onCreateTask = { onCreateTask(column.id) },
                            onEditTask = onEditTask,
                        )
                        AppDivider()
                    }
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
    onSetWipLimit: (Int?) -> Unit,
    onCreateTask: () -> Unit,
    onEditTask: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (editing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ItemGap),
            ) {
                OutlinedTextField(
                    value = editingName,
                    onValueChange = onEditingNameChange,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                ActionIcon(
                    icon = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.column_save),
                    onClick = onConfirmRename,
                    enabled = editingName.isNotBlank(),
                )
                ActionIcon(
                    icon = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.column_cancel),
                    onClick = onCancelEditing,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = column.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                ActionIcon(
                    icon = Icons.Filled.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.column_move_up),
                    onClick = onMoveUp,
                    enabled = position > 0,
                )
                ActionIcon(
                    icon = Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.column_move_down),
                    onClick = onMoveDown,
                    enabled = position < lastPosition,
                )
                ActionIcon(
                    icon = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.column_rename),
                    onClick = onStartEditing,
                )
                ActionIcon(
                    icon = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.column_delete),
                    onClick = onDelete,
                )
            }
        }

        var limitText by rememberSaveable(column.id) { mutableStateOf(column.wipLimit?.toString().orEmpty()) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
        ) {
            OutlinedTextField(
                value = limitText,
                onValueChange = { limitText = it },
                label = { Text(stringResource(R.string.column_wip_limit_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            ActionIcon(
                icon = Icons.Filled.Check,
                contentDescription = stringResource(R.string.column_wip_limit_set),
                onClick = {
                    when (val input = parseWipLimitInput(limitText)) {
                        WipLimitInput.Empty -> onSetWipLimit(null)
                        is WipLimitInput.Value -> onSetWipLimit(input.limit)
                        WipLimitInput.Invalid -> Unit
                    }
                },
                enabled = parseWipLimitInput(limitText) != WipLimitInput.Invalid,
            )
        }

        tasks.forEach { task ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                ActionIcon(
                    icon = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.task_edit),
                    onClick = { onEditTask(task.id) },
                )
            }
        }

        TextButton(onClick = onCreateTask) {
            DecorativeIcon(Icons.Filled.Add)
            Spacer(Modifier.width(Spacing.InlineGap))
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
            onSetWipLimit = { _, _ -> },
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

private fun previewColumn(
    name: String,
    position: Int,
    isDoneColumn: Boolean = false,
): BoardColumn = BoardColumn(
    boardId = "tablero-1",
    name = name,
    position = position,
    wipLimit = null,
    isDoneColumn = isDoneColumn,
    createdAt = Instant.EPOCH,
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
        createdAt = Instant.EPOCH,
        sync = SyncMetadata(
            clientId = "tarea-0",
            updatedAt = Instant.EPOCH,
            pending = false,
            deletedAt = null,
        ),
    ),
)
