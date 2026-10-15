package com.simposio.taskeer.ui.boards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.simposio.taskeer.domain.model.Board
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(R.string.boards_title), style = MaterialTheme.typography.titleLarge)

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(stringResource(R.string.board_name_label)) },
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
                Text(stringResource(R.string.board_create))
            }
        }

        state.error?.let { error ->
            Text(
                text = error.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (state.boards.isEmpty()) {
            Text(text = stringResource(R.string.boards_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.boards, key = { it.id }) { board ->
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = editingName,
                onValueChange = onEditingNameChange,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onConfirmRename, enabled = editingName.isNotBlank()) {
                Text(stringResource(R.string.board_save))
            }
            TextButton(onClick = onCancelEditing) {
                Text(stringResource(R.string.board_cancel))
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = board.name,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen),
            )
            TextButton(onClick = onStartEditing) {
                Text(stringResource(R.string.board_rename))
            }
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.board_delete))
            }
        }
    }
}

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
