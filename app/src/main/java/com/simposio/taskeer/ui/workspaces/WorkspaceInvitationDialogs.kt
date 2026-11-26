package com.simposio.taskeer.ui.workspaces

import android.content.res.Resources
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.workspace.InvitationRules
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.ui.theme.Spacing

@Composable
private fun InviteDialog(
    dialog: InvitationDialog.Invite,
    error: WorkspaceInvitationErrorMessage?,
    submitting: Boolean,
    onConfirm: (String, WorkspaceRole) -> Unit,
    onDismiss: () -> Unit,
) {
    var email by remember { mutableStateOf(dialog.email) }
    var role by remember { mutableStateOf(dialog.role) }
    val errorText = error?.text()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspace_invitations_invite_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.workspace_invitations_email_label)) },
                    singleLine = true,
                    enabled = !submitting,
                    isError = errorText != null,
                    supportingText = errorText?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(email, role) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.workspace_invitations_role_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = Spacing.ItemGap),
                )
                Column(modifier = Modifier.selectableGroup()) {
                    InvitationRules.INVITABLE.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Spacing.MinTouchTarget)
                                .selectable(
                                    selected = role == option,
                                    enabled = !submitting,
                                    role = Role.RadioButton,
                                    onClick = { role = option },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = role == option, onClick = null, enabled = !submitting)
                            Text(
                                text = WorkspaceMembersScreenRules.roleLabel(option).text(),
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
            TextButton(onClick = { onConfirm(email, role) }, enabled = !submitting) {
                Text(stringResource(R.string.workspace_invitations_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.workspace_invitations_cancel)) }
        },
    )
}

@Composable
private fun VerifyEmailDialog(onVerify: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspace_invitations_verify_title)) },
        text = { Text(stringResource(R.string.workspace_invitations_verify_message)) },
        confirmButton = {
            TextButton(onClick = onVerify) { Text(stringResource(R.string.workspace_invitations_verify_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.workspace_invitations_cancel)) }
        },
    )
}

@Composable
private fun ConfirmRevokeDialog(
    email: String,
    submitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspace_invitations_revoke_title)) },
        text = {
            Column {
                Text(stringResource(R.string.workspace_invitations_revoke_message, email))
                if (submitting) SubmittingIndicator()
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !submitting) {
                Text(text = stringResource(R.string.workspace_invitations_revoke), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.workspace_invitations_cancel)) }
        },
    )
}

@Composable
fun WorkspaceInvitationDialogs(state: WorkspaceInvitationsUiState, viewModel: WorkspaceInvitationsViewModel) {
    when (val dialog = state.dialog) {
        InvitationDialog.None -> Unit
        is InvitationDialog.Invite -> InviteDialog(
            dialog = dialog,
            error = state.dialogError,
            submitting = state.submitting,
            onConfirm = viewModel::invite,
            onDismiss = viewModel::dismissDialog,
        )
        is InvitationDialog.VerifyEmail -> VerifyEmailDialog(onVerify = viewModel::verifyEmail, onDismiss = viewModel::dismissDialog)
        is InvitationDialog.ConfirmRevoke -> ConfirmRevokeDialog(
            email = dialog.email,
            submitting = state.submitting,
            onConfirm = viewModel::confirmRevoke,
            onDismiss = viewModel::dismissDialog,
        )
    }
}

internal fun WorkspaceInvitationErrorMessage.text(resources: Resources): String = when (this) {
    WorkspaceInvitationErrorMessage.EmailNotVerified -> resources.getString(R.string.workspace_invitations_error_email_not_verified)
    WorkspaceInvitationErrorMessage.NoPermission -> resources.getString(R.string.workspace_invitations_error_no_permission)
    WorkspaceInvitationErrorMessage.InvalidInvitation -> resources.getString(R.string.workspace_invitations_error_invalid_invitation)
    WorkspaceInvitationErrorMessage.WorkspaceGone -> resources.getString(R.string.workspace_invitations_error_workspace_gone)
    WorkspaceInvitationErrorMessage.CheckEmailAndRole -> resources.getString(R.string.workspace_invitations_error_check_email_and_role)
    WorkspaceInvitationErrorMessage.InvalidEmail -> resources.getString(R.string.workspace_invitations_error_invalid_email)
    WorkspaceInvitationErrorMessage.AlreadyMember -> resources.getString(R.string.workspace_invitations_error_already_member)
    WorkspaceInvitationErrorMessage.AlreadyInvited -> resources.getString(R.string.workspace_invitations_error_already_invited)
    WorkspaceInvitationErrorMessage.InvalidLink -> resources.getString(R.string.workspace_invitations_error_invalid_link)
    is WorkspaceInvitationErrorMessage.RateLimited ->
        if (minutes != null) {
            resources.getQuantityString(R.plurals.workspace_invitations_error_rate_limited_minutes, minutes, minutes)
        } else {
            resources.getString(R.string.workspace_invitations_error_rate_limited)
        }
    WorkspaceInvitationErrorMessage.Offline -> resources.getString(R.string.workspace_invitations_error_offline)
    WorkspaceInvitationErrorMessage.ServerDown -> resources.getString(R.string.workspace_invitations_error_server)
    WorkspaceInvitationErrorMessage.Generic -> resources.getString(R.string.workspace_invitations_error_generic)
}

@Composable
internal fun WorkspaceInvitationErrorMessage.text(): String = text(LocalResources.current)

internal fun InvitationNotice.text(resources: Resources): String = when (this) {
    is InvitationNotice.Invited -> resources.getString(R.string.workspace_invitations_notice_invited, email)
}
