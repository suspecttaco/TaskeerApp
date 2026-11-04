package com.simposio.taskeer.ui.boards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.AppDivider
import com.simposio.taskeer.ui.components.InlineAddField
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
fun BoardsScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BoardsViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BoardsContent(
        state = state,
        onCreate = viewModel::createBoard,
        onRename = viewModel::renameBoard,
        onDelete = viewModel::deleteBoard,
        onOpen = onOpen,
        modifier = modifier,
    )
}

@Composable
fun BoardsContent(
    state: BoardsUiState,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newName by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingName by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
    ) {
        InlineAddField(
            value = newName,
            onValueChange = { newName = it },
            label = stringResource(R.string.board_name_label),
            addDescription = stringResource(R.string.board_create),
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

        if (state.boards.isEmpty()) {
            Text(
                text = stringResource(R.string.boards_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.ItemGap),
            ) {
                items(state.boards, key = { it.id }) { board ->
                    Column {
                        BoardRow(
                            board = board,
                            editing = editingId == board.id,
                            editingName = editingName,
                            onEditingNameChange = { editingName = it },
                            onStartEditing = {
                                editingId = board.id
                                editingName = board.name
                            },
                            onCancelEditing = { editingId = null },
                            onConfirmRename = {
                                onRename(board.id, editingName)
                                editingId = null
                            },
                            onDelete = { onDelete(board.id) },
                            onOpen = { onOpen(board.id) },
                        )
                        AppDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardRow(
    board: Board,
    editing: Boolean,
    editingName: String,
    onEditingNameChange: (String) -> Unit,
    onStartEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    onConfirmRename: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
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
                contentDescription = stringResource(R.string.board_save),
                onClick = onConfirmRename,
                enabled = editingName.isNotBlank(),
            )
            ActionIcon(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.board_cancel),
                onClick = onCancelEditing,
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = board.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = MinTouchTarget)
                    .wrapContentHeight(Alignment.CenterVertically),
            )
            ActionIcon(
                icon = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.board_rename),
                onClick = onStartEditing,
            )
            ActionIcon(
                icon = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.board_delete),
                onClick = onDelete,
            )
        }
    }
}

private val MinTouchTarget = 48.dp

@Preview(showBackground = true)
@Composable
private fun BoardsContentPreview() {
    TaskeerTheme {
        BoardsContent(
            state = BoardsUiState(),
            onCreate = {},
            onRename = { _, _ -> },
            onDelete = {},
            onOpen = {},
        )
    }
}
