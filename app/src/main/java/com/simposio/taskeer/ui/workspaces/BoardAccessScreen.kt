package com.simposio.taskeer.ui.workspaces

import android.content.res.Resources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.workspace.BoardCapability
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.AppDivider
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.StatePage
import com.simposio.taskeer.ui.components.StatePageAction
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
fun BoardAccessScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BoardAccessViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) { viewModel.onOpened() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BoardAccessEvent.ShowError -> announce(
                    context,
                    scope,
                    snackbarHostState,
                    event.message.text(resources),
                    leaving = event.message == BoardRoleErrorMessage.BoardGone,
                )
                is BoardAccessEvent.ShowMessage ->
                    announce(context, scope, snackbarHostState, event.notice.text(resources), leaving = false)
                BoardAccessEvent.LeaveScreen -> onLeave()
            }
        }
    }

    BoardAccessContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::refresh,
        onSetRole = viewModel::openSetRole,
        onRemove = viewModel::requestRemove,
        modifier = modifier,
    )
    BoardAccessDialogs(state, viewModel)
}

@Composable
fun BoardAccessContent(
    state: BoardAccessUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onSetRole: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (state.phase) {
                BoardAccessPhase.LOADING -> LoadingBody()
                BoardAccessPhase.ERROR -> StatePage(
                    icon = Icons.Filled.CloudOff,
                    title = stringResource(R.string.board_roles_access_error_title),
                    message = (state.error ?: BoardRoleErrorMessage.Generic).text(),
                    action = StatePageAction(stringResource(R.string.board_roles_retry), onRefresh),
                )
                BoardAccessPhase.READY -> AccessList(state, onRefresh, onSetRole, onRemove)
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    val description = stringResource(R.string.board_roles_access_loading)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = description })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccessList(
    state: BoardAccessUiState,
    onRefresh: () -> Unit,
    onSetRole: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { RoleHeader(state) }
            if (state.capabilities.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.board_roles_can_title)) }
                items(state.capabilities, key = { it.name }) { capability ->
                    ListItem(
                        leadingContent = { DecorativeIcon(Icons.Filled.Check) },
                        headlineContent = { Text(capability.text()) },
                    )
                }
            }
            if (state.showPanel) panelSection(state, onRefresh, onSetRole, onRemove)
        }
    }
}

@Composable
private fun RoleHeader(state: BoardAccessUiState) {
    val role = state.roleLabel?.let { stringResource(R.string.board_roles_your_role, it.text()) }
        ?: stringResource(R.string.board_roles_role_unavailable)
    ListItem(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        leadingContent = { DecorativeIcon(Icons.Filled.Dashboard) },
        headlineContent = { Text(state.title.ifEmpty { stringResource(R.string.board_roles_access_title) }) },
        supportingContent = { Text(text = role, color = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
    AppDivider()
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = Spacing.ScreenPadding, vertical = Spacing.InlineGap),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(Spacing.ScreenPadding),
    )
}

private fun LazyListScope.panelSection(
    state: BoardAccessUiState,
    onRetry: () -> Unit,
    onSetRole: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    item { SectionTitle(stringResource(R.string.board_roles_panel_title)) }
    if (state.panelLoading) {
        item {
            val description = stringResource(R.string.board_roles_panel_loading)
            Box(modifier = Modifier.fillMaxWidth().padding(Spacing.ScreenPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = description })
            }
        }
    }
    if (state.panelError) {
        item {
            Hint(stringResource(R.string.board_roles_panel_error))
            TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = Spacing.ScreenPadding)) {
                Text(stringResource(R.string.board_roles_retry))
            }
        }
    }
    if (state.noOtherMembers) item { Hint(stringResource(R.string.board_roles_panel_empty)) }
    items(state.rows, key = { it.userId }) { row ->
        OverrideRowItem(row, onSetRole, onRemove)
        AppDivider()
    }
}

@Composable
private fun OverrideRowItem(row: OverrideRowUi, onSetRole: (String) -> Unit, onRemove: (String) -> Unit) {
    ListItem(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        leadingContent = { DecorativeIcon(Icons.Filled.Person) },
        headlineContent = { Text(row.title) },
        supportingContent = {
            Text(
                text = stringResource(R.string.board_roles_row_summary, row.email, row.workspaceRole.text()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = row.boardRole.text(), style = MaterialTheme.typography.labelLarge)
                if (row.actions.isNotEmpty()) RowMenu(row, onSetRole, onRemove)
            }
        },
    )
}

@Composable
private fun RowMenu(row: OverrideRowUi, onSetRole: (String) -> Unit, onRemove: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ActionIcon(
            icon = Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.board_roles_more_actions, row.title),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (OverrideAction.SET_ROLE in row.actions) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.board_roles_set_role)) },
                    onClick = {
                        expanded = false
                        onSetRole(row.userId)
                    },
                )
            }
            if (OverrideAction.REMOVE in row.actions) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.board_roles_remove_override)) },
                    onClick = {
                        expanded = false
                        onRemove(row.userId)
                    },
                )
            }
        }
    }
}

@Composable
private fun BoardCapability.text(): String = stringResource(
    when (this) {
        BoardCapability.VIEW -> R.string.board_roles_can_view
        BoardCapability.EDIT_TASKS -> R.string.board_roles_can_edit_tasks
        BoardCapability.MANAGE_COLUMNS -> R.string.board_roles_can_manage_columns
    },
)

@Composable
private fun BoardRoleDisplay.text(): String = when (this) {
    BoardRoleDisplay.SAME_AS_WORKSPACE -> stringResource(R.string.board_roles_board_role_same)
    BoardRoleDisplay.UNKNOWN -> stringResource(R.string.board_roles_board_role_unknown)
    BoardRoleDisplay.OWNER -> stringResource(R.string.board_roles_board_role_custom, RoleLabel.OWNER.text())
    BoardRoleDisplay.ADMIN -> stringResource(R.string.board_roles_board_role_custom, RoleLabel.ADMIN.text())
    BoardRoleDisplay.EDITOR -> stringResource(R.string.board_roles_board_role_custom, RoleLabel.EDITOR.text())
    BoardRoleDisplay.VIEWER -> stringResource(R.string.board_roles_board_role_custom, RoleLabel.VIEWER.text())
}

internal fun BoardAccessNotice.text(resources: Resources): String = when (this) {
    is BoardAccessNotice.RoleChanged -> resources.getString(
        R.string.board_roles_notice_role_changed,
        resources.getString(
            when (WorkspaceMembersScreenRules.roleLabel(role)) {
                RoleLabel.OWNER -> R.string.workspaces_role_owner
                RoleLabel.ADMIN -> R.string.workspaces_role_admin
                RoleLabel.EDITOR -> R.string.workspaces_role_editor
                RoleLabel.VIEWER -> R.string.workspaces_role_viewer
                RoleLabel.PERSONAL, RoleLabel.MEMBER -> R.string.workspaces_role_member
            },
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun BoardAccessContentPreview() {
    TaskeerTheme {
        BoardAccessContent(
            state = BoardAccessUiState(
                phase = BoardAccessPhase.READY,
                title = "Producto",
                roleLabel = RoleLabel.ADMIN,
                capabilities = listOf(BoardCapability.VIEW, BoardCapability.EDIT_TASKS, BoardCapability.MANAGE_COLUMNS),
                showPanel = true,
                rows = listOf(
                    OverrideRowUi(
                        "1",
                        "Beto",
                        "beto@example.com",
                        RoleLabel.EDITOR,
                        BoardRoleDisplay.VIEWER,
                        setOf(OverrideAction.SET_ROLE, OverrideAction.REMOVE),
                    ),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onRefresh = {},
            onSetRole = {},
            onRemove = {},
        )
    }
}
