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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.simposio.taskeer.ui.components.AppDivider
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.StatePage
import com.simposio.taskeer.ui.components.StatePageAction
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
fun ServerBoardListScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onLeave: () -> Unit,
    onOpenBoard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ServerBoardsViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) { viewModel.onOpened() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ServerBoardsEvent.ShowError -> announce(
                    context,
                    scope,
                    snackbarHostState,
                    event.message.text(resources),
                    leaving = event.message == BoardRoleErrorMessage.BoardGone,
                )
                is ServerBoardsEvent.OpenAccess -> onOpenBoard(event.boardId)
                ServerBoardsEvent.LeaveScreen -> onLeave()
            }
        }
    }

    ServerBoardsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::refresh,
        onOpenBoard = viewModel::openBoard,
        modifier = modifier,
    )
}

@Composable
fun ServerBoardsContent(
    state: ServerBoardsUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onOpenBoard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (state.phase) {
                ServerBoardsPhase.LOADING -> LoadingBody()
                ServerBoardsPhase.ERROR -> StatePage(
                    icon = Icons.Filled.CloudOff,
                    title = stringResource(R.string.board_roles_boards_error_title),
                    message = (state.error ?: BoardRoleErrorMessage.Generic).text(),
                    action = StatePageAction(stringResource(R.string.board_roles_retry), onRefresh),
                )
                ServerBoardsPhase.EMPTY, ServerBoardsPhase.LIST -> BoardsList(state, onRefresh, onOpenBoard)
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    val description = stringResource(R.string.board_roles_boards_loading)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = description })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardsList(state: ServerBoardsUiState, onRefresh: () -> Unit, onOpenBoard: (String) -> Unit) {
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (state.phase == ServerBoardsPhase.EMPTY) {
                item {
                    Text(
                        text = stringResource(R.string.board_roles_boards_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.ScreenPadding),
                    )
                }
            }
            items(state.rows, key = { it.id }) { row ->
                ListItem(
                    modifier = Modifier.clickable(role = Role.Button) { onOpenBoard(row.id) },
                    leadingContent = { DecorativeIcon(Icons.Filled.Dashboard) },
                    headlineContent = { Text(row.name) },
                    trailingContent = { DecorativeIcon(Icons.Filled.ChevronRight) },
                )
                AppDivider()
            }
        }
    }
}

internal fun BoardRoleErrorMessage.text(resources: Resources): String = when (this) {
    BoardRoleErrorMessage.ForbiddenAction -> resources.getString(R.string.board_roles_error_forbidden)
    BoardRoleErrorMessage.BoardGone -> resources.getString(R.string.board_roles_error_board_gone)
    BoardRoleErrorMessage.NotAMember -> resources.getString(R.string.board_roles_error_not_member)
    is BoardRoleErrorMessage.RateLimited ->
        if (minutes != null) {
            resources.getQuantityString(R.plurals.board_roles_error_rate_limited_minutes, minutes, minutes)
        } else {
            resources.getString(R.string.board_roles_error_rate_limited)
        }
    BoardRoleErrorMessage.Offline -> resources.getString(R.string.board_roles_error_offline)
    BoardRoleErrorMessage.ServerDown -> resources.getString(R.string.board_roles_error_server)
    BoardRoleErrorMessage.Generic -> resources.getString(R.string.board_roles_error_generic)
}

@Composable
internal fun BoardRoleErrorMessage.text(): String = text(LocalResources.current)

@Preview(showBackground = true)
@Composable
private fun ServerBoardsContentPreview() {
    TaskeerTheme {
        ServerBoardsContent(
            state = ServerBoardsUiState(
                phase = ServerBoardsPhase.LIST,
                rows = listOf(BoardRowUi("1", "Producto"), BoardRowUi("2", "Marketing")),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onRefresh = {},
            onOpenBoard = {},
        )
    }
}
