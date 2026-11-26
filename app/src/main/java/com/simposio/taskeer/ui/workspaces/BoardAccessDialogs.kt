package com.simposio.taskeer.ui.workspaces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.ui.theme.Spacing

@Composable
private fun ConfirmBoardRoleDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean,
    submitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                if (submitting) SubmittingIndicator()
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !submitting) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.board_roles_cancel)) }
        },
    )
}

@Composable
private fun SetBoardRoleDialog(
    dialog: BoardDialog.SetRole,
    submitting: Boolean,
    onConfirm: (WorkspaceRole) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(dialog.userId) { mutableStateOf<WorkspaceRole?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.board_roles_set_role_title, dialog.name)) },
        text = {
            Column {
                Column(modifier = Modifier.selectableGroup()) {
                    dialog.options.forEach { role ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Spacing.MinTouchTarget)
                                .selectable(
                                    selected = selected == role,
                                    enabled = !submitting,
                                    role = Role.RadioButton,
                                    onClick = { selected = role },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected == role, onClick = null, enabled = !submitting)
                            Text(
                                text = WorkspaceMembersScreenRules.roleLabel(role).text(),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = Spacing.InlineGap),
                            )
                        }
                    }
                }
                if (submitting) SubmittingIndicator()
            }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let(onConfirm) }, enabled = !submitting && selected != null) {
                Text(stringResource(R.string.board_roles_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.board_roles_cancel)) }
        },
    )
}

@Composable
fun BoardAccessDialogs(state: BoardAccessUiState, viewModel: BoardAccessViewModel) {
    when (val dialog = state.dialog) {
        BoardDialog.None -> Unit
        is BoardDialog.SetRole -> SetBoardRoleDialog(
            dialog = dialog,
            submitting = state.submitting,
            onConfirm = viewModel::setRole,
            onDismiss = viewModel::dismissDialog,
        )
        is BoardDialog.ConfirmAdmin -> ConfirmBoardRoleDialog(
            title = stringResource(R.string.board_roles_admin_title),
            message = stringResource(R.string.board_roles_admin_message, dialog.name),
            confirmLabel = stringResource(R.string.board_roles_admin_confirm),
            destructive = false,
            submitting = state.submitting,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismissDialog,
        )
        is BoardDialog.ConfirmRemove -> ConfirmBoardRoleDialog(
            title = stringResource(R.string.board_roles_remove_title),
            message = stringResource(R.string.board_roles_remove_message, dialog.name, dialog.boardName),
            confirmLabel = stringResource(R.string.board_roles_remove_confirm),
            destructive = true,
            submitting = state.submitting,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismissDialog,
        )
    }
}
