package com.simposio.taskeer.ui.inbox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.ui.components.EmptyState
import com.simposio.taskeer.ui.components.QuickAddFab
import com.simposio.taskeer.ui.components.RowAction
import com.simposio.taskeer.ui.components.RowContext
import com.simposio.taskeer.ui.components.TaskRow
import com.simposio.taskeer.ui.components.TaskRowUi
import com.simposio.taskeer.ui.components.actionsIn
import com.simposio.taskeer.ui.lists.ConfirmDeleteListDialog
import com.simposio.taskeer.ui.lists.ListDialog
import com.simposio.taskeer.ui.lists.TaskListNameDialog
import com.simposio.taskeer.ui.lists.TaskListSelector
import com.simposio.taskeer.ui.lists.TaskListSelectorActions
import com.simposio.taskeer.ui.lists.TaskListsUiState
import com.simposio.taskeer.ui.lists.TaskListsViewModel
import com.simposio.taskeer.ui.quickadd.QuickAddContext
import com.simposio.taskeer.ui.quickadd.rememberQuickAddHost
import com.simposio.taskeer.ui.tasks.EmptyScreen
import com.simposio.taskeer.ui.tasks.SwipeHintState
import com.simposio.taskeer.ui.tasks.emptyStateFor
import com.simposio.taskeer.ui.tasks.rememberMoveToListHost
import com.simposio.taskeer.ui.tasks.rememberAssignToBoardHost
import com.simposio.taskeer.ui.tasks.rememberTaskActionsSheet
import com.simposio.taskeer.ui.tasks.rememberTaskEditorHost
import com.simposio.taskeer.ui.tasks.toRow
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
fun InboxScreen(
    viewModelFactory: ViewModelProvider.Factory,
    swipeHint: SwipeHintState,
    modifier: Modifier = Modifier,
) {
    val viewModel: InboxViewModel = viewModel(factory = viewModelFactory)
    val listsViewModel: TaskListsViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listsState by listsViewModel.uiState.collectAsStateWithLifecycle()
    val editorHost = rememberTaskEditorHost(viewModelFactory)
    val openAssign = rememberAssignToBoardHost(viewModelFactory)
    val openMove = rememberMoveToListHost(viewModelFactory)
    val openQuickAdd = rememberQuickAddHost(viewModelFactory, QuickAddContext.List(state.listId))
    val handle: (String, RowAction) -> Unit = { id, action ->
        when (action) {
            RowAction.ASSIGN_TO_BOARD -> openAssign(id)
            RowAction.MOVE_TO_LIST -> state.listId?.let { openMove(id, it) }
            else -> viewModel.onRowAction(id, action)
        }
    }
    val openActions = rememberTaskActionsSheet(
        onAction = { _, id, action -> handle(id, action) },
        onPickDate = { _, id, date -> viewModel.rescheduleTo(id, date) },
    )
    InboxContent(
        state = state,
        listsState = listsState,
        listActions = TaskListSelectorActions(
            onSelect = listsViewModel::select,
            onCreate = listsViewModel::openCreate,
            onRename = listsViewModel::openRename,
            onDelete = listsViewModel::openDelete,
        ),
        onOpenTask = editorHost.openForEdit,
        onRowAction = { row, action ->
            if (action == RowAction.PICK_DATE) openActions(row, RowContext.INBOX, true) else handle(row.id, action)
        },
        onMore = { row -> openActions(row, RowContext.INBOX, false) },
        onAddTask = openQuickAdd,
        swipeHint = swipeHint,
        modifier = modifier,
    )
    ListDialogs(listsState, listsViewModel)
}

@Composable
private fun ListDialogs(state: TaskListsUiState, viewModel: TaskListsViewModel) {
    when (val dialog = state.dialog) {
        ListDialog.None -> Unit
        ListDialog.Create -> TaskListNameDialog(
            title = stringResource(R.string.list_new_title),
            confirmLabel = stringResource(R.string.list_create),
            initialName = "",
            error = state.error,
            onConfirm = viewModel::submitName,
            onDismiss = viewModel::dismiss,
        )
        is ListDialog.Rename -> TaskListNameDialog(
            title = stringResource(R.string.list_rename_title),
            confirmLabel = stringResource(R.string.list_rename_confirm),
            initialName = dialog.currentName,
            error = state.error,
            onConfirm = viewModel::submitName,
            onDismiss = viewModel::dismiss,
        )
        is ListDialog.ConfirmDelete -> ConfirmDeleteListDialog(
            dialog = dialog,
            error = state.error,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismiss,
        )
    }
}

@Composable
fun InboxContent(
    state: InboxUiState,
    listsState: TaskListsUiState,
    listActions: TaskListSelectorActions,
    onOpenTask: (String) -> Unit,
    onRowAction: (TaskRowUi, RowAction) -> Unit,
    onMore: (TaskRowUi) -> Unit,
    onAddTask: () -> Unit,
    swipeHint: SwipeHintState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TaskListSelector(state = listsState, actions = listActions)
            state.error?.let { error ->
                Text(
                    text = error.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(Spacing.ScreenPadding),
                )
            }
            if (state.items.isEmpty()) {
                val spec = emptyStateFor(EmptyScreen.LIST, hasTasks = false, filterActive = false)
                EmptyState(
                    icon = Icons.Filled.Inbox,
                    text = stringResource(spec.textRes),
                    actionLabel = stringResource(spec.actionRes),
                    onAction = onAddTask,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = Spacing.FabClearance),
                ) {
                    items(state.items, key = { it.id }) { task ->
                        val row = task.toRow()
                        TaskRow(
                            row = row,
                            actions = row.actionsIn(RowContext.INBOX),
                            onAction = { onRowAction(row, it) },
                            onOpen = { onOpenTask(row.id) },
                            onMore = { onMore(row) },
                            peek = swipeHint.shouldPlay() && task.id == state.items.first().id,
                            onPeekDone = swipeHint::markPlayed,
                        )
                    }
                }
            }
        }
        QuickAddFab(
            onClick = onAddTask,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.ScreenPadding),
        )
    }
}

@Preview(showBackground = true, heightDp = 480)
@Composable
private fun InboxContentPreview() {
    TaskeerTheme {
        InboxContent(
            state = InboxUiState(
                items = listOf(
                    InboxTaskUi("t1", "Comprar pan", TaskPriority.MEDIUM, null, isOverdue = false, isMarked = true),
                    InboxTaskUi("t3", "Pagar la luz", TaskPriority.LOW, null, isOverdue = false, isMarked = false, isCompleted = true),
                    InboxTaskUi("t2", "Llamar al banco", TaskPriority.HIGH, "1 oct 2026, 9:00", isOverdue = true, isMarked = false),
                ),
            ),
            listsState = TaskListsUiState(),
            listActions = TaskListSelectorActions({}, {}, {}, {}),
            onOpenTask = {},
            onRowAction = { _, _ -> },
            onMore = {},
            onAddTask = {},
            swipeHint = SwipeHintState(played = true),
        )
    }
}

@Preview(showBackground = true, heightDp = 480)
@Composable
private fun InboxEmptyPreview() {
    TaskeerTheme {
        InboxContent(
            state = InboxUiState(),
            listsState = TaskListsUiState(),
            listActions = TaskListSelectorActions({}, {}, {}, {}),
            onOpenTask = {},
            onRowAction = { _, _ -> },
            onMore = {},
            onAddTask = {},
            swipeHint = SwipeHintState(played = true),
        )
    }
}
