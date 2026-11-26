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
private fun ConfirmMemberDialog(
    title: String,
    message: String,
    confirmLabel: String,
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
                Text(text = confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.workspace_members_cancel)) }
        },
    )
}

@Composable
private fun ChangeRoleDialog(
    dialog: MemberDialog.ChangeRole,
    submitting: Boolean,
    onConfirm: (WorkspaceRole) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(dialog.userId) { mutableStateOf<WorkspaceRole?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspace_members_change_role_title, dialog.name)) },
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
                Text(stringResource(R.string.workspace_members_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.workspace_members_cancel)) }
        },
    )
}

@Composable
fun WorkspaceMemberDialogs(state: WorkspaceMembersUiState, viewModel: WorkspaceMembersViewModel) {
    when (val dialog = state.dialog) {
        MemberDialog.None -> Unit
        is MemberDialog.ChangeRole -> ChangeRoleDialog(
            dialog = dialog,
            submitting = state.submitting,
            onConfirm = viewModel::changeRole,
            onDismiss = viewModel::dismissDialog,
        )
        is MemberDialog.ConfirmRemove -> ConfirmMemberDialog(
            title = stringResource(R.string.workspace_members_remove_title),
            message = stringResource(R.string.workspace_members_remove_message, dialog.name, state.title),
            confirmLabel = stringResource(R.string.workspace_members_remove),
            submitting = state.submitting,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismissDialog,
        )
        MemberDialog.ConfirmLeave -> ConfirmMemberDialog(
            title = stringResource(R.string.workspace_members_leave),
            message = stringResource(R.string.workspace_members_leave_message, state.title),
            confirmLabel = stringResource(R.string.workspace_members_leave_confirm),
            submitting = state.submitting,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismissDialog,
        )
        is MemberDialog.ConfirmTransfer -> ConfirmMemberDialog(
            title = stringResource(R.string.workspace_members_transfer_title),
            message = stringResource(R.string.workspace_members_transfer_message, dialog.name, state.title),
            confirmLabel = stringResource(R.string.workspace_members_transfer_confirm),
            submitting = state.submitting,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismissDialog,
        )
    }
}
