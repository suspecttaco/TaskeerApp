package com.simposio.taskeer.ui.boards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.DomainError

@Composable
fun BoardNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    error: DomainError?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.board_name_label)) },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(name) }),
                )
                error?.let {
                    Text(
                        text = it.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.board_cancel)) } },
    )
}

@Composable
fun ConfirmDeleteBoardDialog(
    dialog: BoardDialog.ConfirmDelete,
    error: DomainError?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.board_delete_title, dialog.name)) },
        text = {
            Column {
                Text(stringResource(R.string.board_delete_message))
                error?.let {
                    Text(text = it.message, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.board_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.board_cancel)) } },
    )
}

@Composable
fun BoardDialogs(state: BoardsUiState, viewModel: BoardsViewModel) {
    when (val dialog = state.dialog) {
        BoardDialog.None -> Unit
        BoardDialog.Create -> BoardNameDialog(
            title = stringResource(R.string.board_new_title),
            confirmLabel = stringResource(R.string.board_create),
            initialName = "",
            error = state.error,
            onConfirm = viewModel::submitName,
            onDismiss = viewModel::dismiss,
        )
        is BoardDialog.Rename -> BoardNameDialog(
            title = stringResource(R.string.board_rename_title),
            confirmLabel = stringResource(R.string.board_save),
            initialName = dialog.currentName,
            error = state.error,
            onConfirm = viewModel::submitName,
            onDismiss = viewModel::dismiss,
        )
        is BoardDialog.ConfirmDelete -> ConfirmDeleteBoardDialog(
            dialog = dialog,
            error = state.error,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismiss,
        )
    }
}
