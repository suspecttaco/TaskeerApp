package com.simposio.taskeer.ui.workspaces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.theme.Spacing

@Composable
fun WorkspaceNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    error: WorkspaceErrorMessage?,
    submitting: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val errorText = error?.text()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.workspaces_name_label)) },
                    singleLine = true,
                    enabled = !submitting,
                    isError = errorText != null,
                    supportingText = errorText?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(name) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (submitting) SubmittingIndicator()
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = !submitting) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.workspaces_cancel)) }
        },
    )
}

@Composable
fun ConfirmDeleteWorkspaceDialog(
    name: String,
    submitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspaces_delete_title, name)) },
        text = {
            Column {
                Text(stringResource(R.string.workspaces_delete_message))
                if (submitting) SubmittingIndicator()
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !submitting) {
                Text(text = stringResource(R.string.workspaces_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.workspaces_cancel)) }
        },
    )
}

@Composable
internal fun SubmittingIndicator() {
    val description = stringResource(R.string.workspaces_saving)
    Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.ItemGap)) {
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = description })
    }
}

@Composable
fun WorkspaceDialogs(state: WorkspacesUiState, viewModel: WorkspacesViewModel) {
    when (val dialog = state.dialog) {
        WorkspaceDialog.None -> Unit
        WorkspaceDialog.Create -> WorkspaceNameDialog(
            title = stringResource(R.string.workspaces_new_title),
            confirmLabel = stringResource(R.string.workspaces_create),
            initialName = "",
            error = state.dialogError,
            submitting = state.submitting,
            onConfirm = viewModel::confirmName,
            onDismiss = viewModel::dismissDialog,
        )
        is WorkspaceDialog.Rename -> WorkspaceNameDialog(
            title = stringResource(R.string.workspaces_rename_title),
            confirmLabel = stringResource(R.string.workspaces_save),
            initialName = dialog.currentName,
            error = state.dialogError,
            submitting = state.submitting,
            onConfirm = viewModel::confirmName,
            onDismiss = viewModel::dismissDialog,
        )
        is WorkspaceDialog.ConfirmDelete -> ConfirmDeleteWorkspaceDialog(
            name = dialog.name,
            submitting = state.submitting,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDialog,
        )
    }
}
