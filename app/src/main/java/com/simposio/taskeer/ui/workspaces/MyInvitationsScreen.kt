package com.simposio.taskeer.ui.workspaces

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.AppDivider
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.StatePage
import com.simposio.taskeer.ui.components.StatePageAction
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import java.time.Instant

@Composable
fun MyInvitationsScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MyInvitationsViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    // Estado sin guardado: el enlace es un secreto efímero y no debe pasar por el Bundle.
    val linkState = remember { TextFieldState() }

    LaunchedEffect(viewModel) { viewModel.onOpened() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is InboxEvent.ShowError -> snackbarHostState.showSnackbar(event.message.text(resources))
                is InboxEvent.ShowMessage -> snackbarHostState.showSnackbar(event.notice.text(resources))
                InboxEvent.LeaveScreen -> onLeave()
            }
        }
    }
    LaunchedEffect(state.dialog) { if (state.dialog == InboxDialog.NONE) linkState.clearText() }
    DisposableEffect(linkState) { onDispose { linkState.clearText() } }

    MyInvitationsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::refresh,
        onAccept = viewModel::accept,
        onAcceptWithLink = viewModel::openAcceptWithLink,
        modifier = modifier,
    )
    if (state.dialog == InboxDialog.ACCEPT_WITH_LINK) {
        AcceptWithLinkDialog(
            linkState = linkState,
            error = state.dialogError,
            submitting = state.submitting,
            onConfirm = { viewModel.acceptWithLink(linkState.text.toString()) },
            onDismiss = viewModel::dismissDialog,
        )
    }
}

@Composable
fun MyInvitationsContent(
    state: MyInvitationsUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onAccept: (String) -> Unit,
    onAcceptWithLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (state.phase) {
                InboxPhase.LOADING -> LoadingBody()
                InboxPhase.ERROR -> StatePage(
                    icon = Icons.Filled.CloudOff,
                    title = stringResource(R.string.workspace_invitations_inbox_error_title),
                    message = (state.error ?: WorkspaceInvitationErrorMessage.Generic).text(),
                    action = StatePageAction(stringResource(R.string.workspace_invitations_retry), onRefresh),
                )
                InboxPhase.EMPTY, InboxPhase.LIST -> InboxList(state, onRefresh, onAccept, onAcceptWithLink)
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    val description = stringResource(R.string.workspace_invitations_inbox_loading)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = description })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxList(
    state: MyInvitationsUiState,
    onRefresh: () -> Unit,
    onAccept: (String) -> Unit,
    onAcceptWithLink: () -> Unit,
) {
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onAcceptWithLink),
                    leadingContent = { DecorativeIcon(Icons.Filled.Link) },
                    headlineContent = { Text(stringResource(R.string.workspace_invitations_accept_with_link)) },
                    supportingContent = { Text(stringResource(R.string.workspace_invitations_link_hint)) },
                )
                AppDivider()
            }
            if (state.phase == InboxPhase.EMPTY) {
                item {
                    Text(
                        text = stringResource(R.string.workspace_invitations_inbox_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.ScreenPadding),
                    )
                }
            }
            items(state.rows, key = { it.id }) { row ->
                InboxRow(row, onAccept)
                AppDivider()
            }
        }
    }
}

@Composable
private fun InboxRow(row: MyInvitationRowUi, onAccept: (String) -> Unit) {
    val summary = stringResource(R.string.workspace_invitations_row_summary, row.role.text(), expiryText(row.expired, row.expiresAt))
    ListItem(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        leadingContent = { DecorativeIcon(if (row.expired) Icons.Filled.Schedule else Icons.Filled.Mail) },
        headlineContent = { Text(row.workspaceName) },
        supportingContent = {
            Text(text = summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            when {
                row.accepting -> CircularProgressIndicator(modifier = Modifier.padding(Spacing.InlineGap))
                row.canAccept -> TextButton(onClick = { onAccept(row.id) }) {
                    Text(stringResource(R.string.workspace_invitations_accept))
                }
            }
        },
    )
}

@Composable
private fun AcceptWithLinkDialog(
    linkState: TextFieldState,
    error: WorkspaceInvitationErrorMessage?,
    submitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val errorText = error?.text()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspace_invitations_link_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.workspace_invitations_link_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = Spacing.ItemGap),
                )
                OutlinedTextField(
                    state = linkState,
                    label = { Text(stringResource(R.string.workspace_invitations_link_label)) },
                    enabled = !submitting,
                    isError = errorText != null,
                    supportingText = errorText?.let { message -> { Text(message) } },
                    lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = MAX_LINK_LINES),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    onKeyboardAction = KeyboardActionHandler { onConfirm() },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (submitting) SubmittingIndicator()
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !submitting) { Text(stringResource(R.string.workspace_invitations_accept)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.workspace_invitations_cancel)) }
        },
    )
}

internal fun InboxNotice.text(resources: Resources): String = when (this) {
    is InboxNotice.Joined ->
        if (workspaceName.isNotEmpty()) {
            resources.getString(R.string.workspace_invitations_notice_joined, workspaceName)
        } else {
            resources.getString(R.string.workspace_invitations_notice_joined_generic)
        }
    is InboxNotice.AlreadyMember ->
        if (workspaceName.isNotEmpty()) {
            resources.getString(R.string.workspace_invitations_notice_already_member, workspaceName)
        } else {
            resources.getString(R.string.workspace_invitations_notice_already_member_generic)
        }
}

private const val MAX_LINK_LINES = 4

@Preview(showBackground = true)
@Composable
private fun MyInvitationsContentPreview() {
    TaskeerTheme {
        MyInvitationsContent(
            state = MyInvitationsUiState(
                phase = InboxPhase.LIST,
                rows = listOf(
                    MyInvitationRowUi("1", "Equipo de diseño", RoleLabel.EDITOR, Instant.parse("2026-12-20T10:00:00Z"), false, true, false),
                    MyInvitationRowUi("2", "Marketing", RoleLabel.VIEWER, Instant.parse("2026-11-01T10:00:00Z"), true, false, false),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onRefresh = {},
            onAccept = {},
            onAcceptWithLink = {},
        )
    }
}
