package com.simposio.taskeer.ui.workspaces

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.AppDivider
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.StatePage
import com.simposio.taskeer.ui.components.StatePageAction
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
fun WorkspacesScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onLeave: () -> Unit,
    onOpenMembers: (String) -> Unit,
    onOpenInvitations: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WorkspacesViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) { viewModel.onOpened() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WorkspacesEvent.ShowError -> snackbarHostState.showSnackbar(event.message.text(resources))
                is WorkspacesEvent.OpenMembers -> onOpenMembers(event.workspaceId)
                WorkspacesEvent.OpenInvitations -> onOpenInvitations()
                WorkspacesEvent.LeaveScreen -> onLeave()
            }
        }
    }

    WorkspacesContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::refresh,
        onNew = viewModel::openCreate,
        onRename = viewModel::openRename,
        onDelete = viewModel::requestDelete,
        onOpen = viewModel::openMembers,
        onOpenInvitations = viewModel::openInvitations,
        modifier = modifier,
    )
    WorkspaceDialogs(state, viewModel)
}

@Composable
fun WorkspacesContent(
    state: WorkspacesUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onNew: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (String) -> Unit,
    onOpenInvitations: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { DecorativeIcon(Icons.Filled.Add) },
                text = { Text(stringResource(R.string.workspaces_new)) },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (state.phase) {
                WorkspacesPhase.LOADING -> LoadingBody()
                WorkspacesPhase.ERROR -> StatePage(
                    icon = Icons.Filled.CloudOff,
                    title = stringResource(R.string.workspaces_error_title),
                    message = (state.error ?: WorkspaceErrorMessage.Generic).text(),
                    action = StatePageAction(stringResource(R.string.workspaces_retry), onRefresh),
                )
                WorkspacesPhase.EMPTY, WorkspacesPhase.LIST -> WorkspacesList(state, onRefresh, onRename, onDelete, onOpen, onOpenInvitations)
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    val description = stringResource(R.string.workspaces_loading)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = description })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspacesList(
    state: WorkspacesUiState,
    onRefresh: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (String) -> Unit,
    onOpenInvitations: () -> Unit,
) {
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (state.showInvitations) {
                item {
                    ListItem(
                        modifier = Modifier.clickable(onClick = onOpenInvitations),
                        leadingContent = { DecorativeIcon(Icons.Filled.Mail) },
                        headlineContent = { Text(stringResource(R.string.workspace_invitations_inbox_row)) },
                    )
                    AppDivider()
                }
            }
            if (state.phase == WorkspacesPhase.EMPTY) {
                item {
                    Text(
                        text = stringResource(R.string.workspaces_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(Spacing.ScreenPadding),
                    )
                }
            }
            items(state.rows, key = { it.id }) { row ->
                WorkspaceRow(row = row, onRename = onRename, onDelete = onDelete, onOpen = onOpen)
                AppDivider()
            }
        }
    }
}

@Composable
private fun WorkspaceRow(row: WorkspaceRowUi, onRename: (String) -> Unit, onDelete: (String) -> Unit, onOpen: (String) -> Unit) {
    ListItem(
        modifier = Modifier
            .clickable(onClickLabel = stringResource(R.string.workspaces_open_members)) { onOpen(row.id) }
            .semantics(mergeDescendants = true) {},
        headlineContent = { Text(row.name) },
        supportingContent = { Text(text = row.role.text(), color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = {
            if (row.actions.isNotEmpty()) RowMenu(row = row, onRename = onRename, onDelete = onDelete)
        },
    )
}

@Composable
private fun RowMenu(row: WorkspaceRowUi, onRename: (String) -> Unit, onDelete: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ActionIcon(
            icon = Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.workspaces_more_actions, row.name),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (RowAction.RENAME in row.actions) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.workspaces_rename)) },
                    onClick = {
                        expanded = false
                        onRename(row.id)
                    },
                )
            }
            if (RowAction.DELETE in row.actions) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.workspaces_delete)) },
                    onClick = {
                        expanded = false
                        onDelete(row.id)
                    },
                )
            }
        }
    }
}

@Composable
internal fun WorkspaceErrorMessage.text(): String = text(LocalResources.current)

internal fun WorkspaceErrorMessage.text(resources: Resources): String = when (this) {
    WorkspaceErrorMessage.PlanLimit -> resources.getString(R.string.workspaces_error_plan_limit)
    WorkspaceErrorMessage.OnlyOwnerCanDelete -> resources.getString(R.string.workspaces_error_only_owner)
    WorkspaceErrorMessage.NoPermission -> resources.getString(R.string.workspaces_error_no_permission)
    WorkspaceErrorMessage.EmailNotVerified -> resources.getString(R.string.workspaces_error_email_not_verified)
    WorkspaceErrorMessage.InvalidName -> resources.getString(R.string.workspaces_error_name_invalid)
    WorkspaceErrorMessage.CheckName -> resources.getString(R.string.workspaces_error_check_name)
    WorkspaceErrorMessage.Unavailable -> resources.getString(R.string.workspaces_error_unavailable)
    is WorkspaceErrorMessage.RateLimited ->
        if (minutes != null) {
            resources.getQuantityString(R.plurals.workspaces_error_rate_limited_minutes, minutes, minutes)
        } else {
            resources.getString(R.string.workspaces_error_rate_limited)
        }
    WorkspaceErrorMessage.Offline -> resources.getString(R.string.workspaces_error_offline)
    WorkspaceErrorMessage.ServerDown -> resources.getString(R.string.workspaces_error_server)
    WorkspaceErrorMessage.Generic -> resources.getString(R.string.workspaces_error_generic)
}

@Composable
internal fun RoleLabel.text(): String = stringResource(
    when (this) {
        RoleLabel.PERSONAL -> R.string.workspaces_role_personal
        RoleLabel.OWNER -> R.string.workspaces_role_owner
        RoleLabel.ADMIN -> R.string.workspaces_role_admin
        RoleLabel.EDITOR -> R.string.workspaces_role_editor
        RoleLabel.VIEWER -> R.string.workspaces_role_viewer
        RoleLabel.MEMBER -> R.string.workspaces_role_member
    },
)

@Preview(showBackground = true)
@Composable
private fun WorkspacesContentPreview() {
    TaskeerTheme {
        WorkspacesContent(
            state = WorkspacesUiState(
                phase = WorkspacesPhase.LIST,
                rows = listOf(
                    WorkspaceRowUi("1", "Personal", RoleLabel.PERSONAL, setOf(RowAction.RENAME)),
                    WorkspaceRowUi("2", "Equipo de diseño", RoleLabel.OWNER, setOf(RowAction.RENAME, RowAction.DELETE)),
                    WorkspaceRowUi("3", "Marketing", RoleLabel.VIEWER, emptySet()),
                ),
                showInvitations = true,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onRefresh = {},
            onNew = {},
            onRename = {},
            onDelete = {},
            onOpen = {},
            onOpenInvitations = {},
        )
    }
}
