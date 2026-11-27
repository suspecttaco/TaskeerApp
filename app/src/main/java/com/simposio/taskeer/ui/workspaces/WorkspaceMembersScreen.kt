package com.simposio.taskeer.ui.workspaces

import android.content.Context
import android.content.res.Resources
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.simposio.taskeer.ui.components.EmptyHint
import com.simposio.taskeer.ui.components.LoadingBody
import com.simposio.taskeer.ui.components.NoticeKind
import com.simposio.taskeer.ui.components.PhaseCrossfade
import com.simposio.taskeer.ui.components.PhaseKind
import com.simposio.taskeer.ui.components.StatePage
import com.simposio.taskeer.ui.components.StatePageAction
import com.simposio.taskeer.ui.components.phaseKindOf
import com.simposio.taskeer.ui.components.rememberWhile
import com.simposio.taskeer.ui.components.rowItemModifier
import com.simposio.taskeer.ui.components.showNotice
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun WorkspaceMembersScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onLeave: () -> Unit,
    onVerifyEmail: () -> Unit,
    onOpenBoards: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WorkspaceMembersViewModel = viewModel(factory = viewModelFactory)
    val invitationsViewModel: WorkspaceInvitationsViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val invitations by invitationsViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Los dos ViewModels pueden pedir salir a la vez (sesión cerrada, workspace desaparecido): solo se sale una vez.
    val leaveOnce = remember(onLeave) {
        var left = false
        {
            if (!left) {
                left = true
                onLeave()
            }
        }
    }

    LaunchedEffect(viewModel) { viewModel.onOpened() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MembersEvent.ShowError ->
                    announce(context, scope, snackbarHostState, event.message.text(resources), NoticeKind.ERROR, leaving = event.message == WorkspaceMemberErrorMessage.WorkspaceGone)
                is MembersEvent.ShowMessage ->
                    announce(context, scope, snackbarHostState, event.notice.text(resources), NoticeKind.INFO, leaving = event.notice is MembersNotice.Left)
                is MembersEvent.OpenBoards -> onOpenBoards(event.workspaceId)
                MembersEvent.LeaveScreen -> leaveOnce()
            }
        }
    }
    LaunchedEffect(invitationsViewModel) {
        invitationsViewModel.events.collect { event ->
            when (event) {
                is InvitationEvent.ShowError -> announce(
                    context,
                    scope,
                    snackbarHostState,
                    event.message.text(resources),
                    NoticeKind.ERROR,
                    leaving = event.message == WorkspaceInvitationErrorMessage.WorkspaceGone,
                )
                is InvitationEvent.ShowMessage ->
                    announce(context, scope, snackbarHostState, event.notice.text(resources), NoticeKind.INFO, leaving = false)
                InvitationEvent.OpenVerifyEmail -> onVerifyEmail()
                InvitationEvent.LeaveScreen -> leaveOnce()
            }
        }
    }

    WorkspaceMembersContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onRefresh = {
            viewModel.refresh()
            invitationsViewModel.refresh()
        },
        invitations = invitations,
        onInvite = invitationsViewModel::openInvite,
        onRevokeInvitation = invitationsViewModel::requestRevoke,
        onRetryInvitations = invitationsViewModel::refresh,
        onChangeRole = viewModel::openChangeRole,
        onRemove = viewModel::requestRemove,
        onLeave = viewModel::requestLeave,
        onTransfer = viewModel::requestTransfer,
        onOpenBoards = viewModel::openBoards,
        modifier = modifier,
    )
    WorkspaceMemberDialogs(state, viewModel)
    WorkspaceInvitationDialogs(invitations, invitationsViewModel)
}

// Si la pantalla se cierra a continuación, su Snackbar desaparecería con ella: el aviso va en un Toast.
internal fun announce(
    context: Context,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    text: String,
    kind: NoticeKind,
    leaving: Boolean,
) {
    if (leaving) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    } else {
        scope.launch { snackbarHostState.showNotice(text, kind) }
    }
}

@Composable
fun WorkspaceMembersContent(
    state: WorkspaceMembersUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onChangeRole: (String) -> Unit,
    onRemove: (String) -> Unit,
    onLeave: () -> Unit,
    onTransfer: (String) -> Unit,
    modifier: Modifier = Modifier,
    invitations: WorkspaceInvitationsUiState = WorkspaceInvitationsUiState(),
    onInvite: () -> Unit = {},
    onRevokeInvitation: (String) -> Unit = {},
    onRetryInvitations: () -> Unit = {},
    onOpenBoards: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            val shownError = rememberWhile(state.phase == MembersPhase.ERROR, state.error)
            PhaseCrossfade(
                kind = phaseKindOf(loading = state.phase == MembersPhase.LOADING, error = state.phase == MembersPhase.ERROR),
                modifier = Modifier.fillMaxSize(),
            ) { kind ->
                when (kind) {
                    PhaseKind.LOADING -> LoadingBody(stringResource(R.string.workspace_members_loading))
                    PhaseKind.ERROR -> StatePage(
                        icon = Icons.Filled.CloudOff,
                        title = stringResource(R.string.workspace_members_error_title),
                        message = (shownError ?: WorkspaceMemberErrorMessage.Generic).text(),
                        action = StatePageAction(stringResource(R.string.workspace_members_retry), onRefresh),
                    )
                    PhaseKind.CONTENT ->
                        MembersList(
                            state, invitations, onRefresh, onChangeRole, onRemove, onLeave, onTransfer,
                            onInvite, onRevokeInvitation, onRetryInvitations, onOpenBoards,
                        )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MembersList(
    state: WorkspaceMembersUiState,
    invitations: WorkspaceInvitationsUiState,
    onRefresh: () -> Unit,
    onChangeRole: (String) -> Unit,
    onRemove: (String) -> Unit,
    onLeave: () -> Unit,
    onTransfer: (String) -> Unit,
    onInvite: () -> Unit,
    onRevokeInvitation: (String) -> Unit,
    onRetryInvitations: () -> Unit,
    onOpenBoards: () -> Unit,
) {
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                ListItem(
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenBoards),
                    leadingContent = { DecorativeIcon(Icons.Filled.Dashboard) },
                    headlineContent = { Text(stringResource(R.string.board_roles_members_row)) },
                    trailingContent = { DecorativeIcon(Icons.Filled.ChevronRight) },
                )
                AppDivider()
            }
            item {
                Text(
                    text = stringResource(R.string.workspace_members_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = Spacing.ScreenPadding, vertical = Spacing.InlineGap),
                )
            }
            if (state.phase == MembersPhase.EMPTY) {
                item { EmptyHint(text = stringResource(R.string.workspace_members_empty), modifier = Modifier.padding(Spacing.ScreenPadding)) }
            }
            if (state.ownerHint) {
                item { EmptyHint(text = stringResource(R.string.workspace_members_owner_hint), modifier = Modifier.padding(Spacing.ScreenPadding)) }
            }
            items(state.rows, key = { it.userId }) { row ->
                Column(modifier = rowItemModifier()) {
                    MemberRow(row, onChangeRole, onRemove, onLeave, onTransfer)
                    AppDivider()
                }
            }
            invitationsSection(invitations, onInvite, onRevokeInvitation, onRetryInvitations)
        }
    }
}

@Composable
private fun MemberRow(
    row: MemberRowUi,
    onChangeRole: (String) -> Unit,
    onRemove: (String) -> Unit,
    onLeave: () -> Unit,
    onTransfer: (String) -> Unit,
) {
    ListItem(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        leadingContent = { DecorativeIcon(Icons.Filled.Person) },
        headlineContent = {
            Text(if (row.isSelf) stringResource(R.string.workspace_members_self_name, row.title) else row.title)
        },
        supportingContent = { Text(text = row.email, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = row.role.text(), style = MaterialTheme.typography.labelLarge)
                if (row.actions.isNotEmpty()) RowMenu(row, onChangeRole, onRemove, onLeave, onTransfer)
            }
        },
    )
}

@Composable
private fun RowMenu(
    row: MemberRowUi,
    onChangeRole: (String) -> Unit,
    onRemove: (String) -> Unit,
    onLeave: () -> Unit,
    onTransfer: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ActionIcon(
            icon = Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.workspace_members_more_actions, row.title),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MemberMenuItem(row, MemberAction.CHANGE_ROLE, R.string.workspace_members_change_role, { expanded = false }) {
                onChangeRole(row.userId)
            }
            MemberMenuItem(row, MemberAction.TRANSFER, R.string.workspace_members_transfer, { expanded = false }) {
                onTransfer(row.userId)
            }
            MemberMenuItem(row, MemberAction.REMOVE, R.string.workspace_members_remove, { expanded = false }) {
                onRemove(row.userId)
            }
            MemberMenuItem(row, MemberAction.LEAVE, R.string.workspace_members_leave, { expanded = false }) { onLeave() }
        }
    }
}

@Composable
private fun MemberMenuItem(
    row: MemberRowUi,
    action: MemberAction,
    label: Int,
    close: () -> Unit,
    onClick: () -> Unit,
) {
    if (action !in row.actions) return
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = {
            close()
            onClick()
        },
    )
}

internal fun WorkspaceMemberErrorMessage.text(resources: Resources): String = when (this) {
    WorkspaceMemberErrorMessage.ForbiddenAction -> resources.getString(R.string.workspace_members_error_forbidden)
    WorkspaceMemberErrorMessage.OwnerRoleImmutable -> resources.getString(R.string.workspace_members_error_owner_role)
    WorkspaceMemberErrorMessage.OwnerMustTransfer -> resources.getString(R.string.workspace_members_error_owner_must_transfer)
    WorkspaceMemberErrorMessage.TransferToSelf -> resources.getString(R.string.workspace_members_error_transfer_self)
    WorkspaceMemberErrorMessage.NotAMember -> resources.getString(R.string.workspace_members_error_not_member)
    WorkspaceMemberErrorMessage.MemberGone -> resources.getString(R.string.workspace_members_error_member_gone)
    WorkspaceMemberErrorMessage.WorkspaceGone -> resources.getString(R.string.workspace_members_error_workspace_gone)
    is WorkspaceMemberErrorMessage.RateLimited ->
        if (minutes != null) {
            resources.getQuantityString(R.plurals.workspace_members_error_rate_limited_minutes, minutes, minutes)
        } else {
            resources.getString(R.string.workspace_members_error_rate_limited)
        }
    WorkspaceMemberErrorMessage.Offline -> resources.getString(R.string.workspace_members_error_offline)
    WorkspaceMemberErrorMessage.ServerDown -> resources.getString(R.string.workspace_members_error_server)
    WorkspaceMemberErrorMessage.Generic -> resources.getString(R.string.workspace_members_error_generic)
}

@Composable
internal fun WorkspaceMemberErrorMessage.text(): String = text(LocalResources.current)

internal fun MembersNotice.text(resources: Resources): String = when (this) {
    is MembersNotice.Left -> resources.getString(R.string.workspace_members_notice_left, workspaceName)
    is MembersNotice.OwnershipTransferred -> resources.getString(R.string.workspace_members_notice_transferred, memberName)
}

@Preview(showBackground = true)
@Composable
private fun WorkspaceMembersContentPreview() {
    TaskeerTheme {
        WorkspaceMembersContent(
            state = WorkspaceMembersUiState(
                phase = MembersPhase.LIST,
                title = "Equipo de diseño",
                rows = listOf(
                    MemberRowUi("1", "Ana", "ana@example.com", RoleLabel.OWNER, isSelf = true, actions = emptySet()),
                    MemberRowUi("2", "Beto", "beto@example.com", RoleLabel.EDITOR, isSelf = false, actions = setOf(MemberAction.REMOVE)),
                    MemberRowUi("3", "Carla", "carla@example.com", RoleLabel.VIEWER, isSelf = false, actions = emptySet()),
                ),
                ownerHint = true,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onRefresh = {},
            onChangeRole = {},
            onRemove = {},
            onLeave = {},
            onTransfer = {},
        )
    }
}
