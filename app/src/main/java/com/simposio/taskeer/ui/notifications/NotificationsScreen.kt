package com.simposio.taskeer.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.permission.NotificationPermissionNotice
import com.simposio.taskeer.ui.tasks.rememberTaskEditorHost

@Composable
fun NotificationsScreen(viewModelFactory: ViewModelProvider.Factory, modifier: Modifier = Modifier) {
    val viewModel: NotificationsViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editorHost = rememberTaskEditorHost(viewModelFactory)
    val snackbarHostState = remember { SnackbarHostState() }
    val taskGoneMessage = stringResource(R.string.notifications_task_gone)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is NotificationsEvent.OpenTask -> editorHost.openForEdit(event.taskId)
                NotificationsEvent.ShowTaskGone -> snackbarHostState.showSnackbar(taskGoneMessage)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        NotificationsContent(
            state = state,
            onOpen = viewModel::onOpen,
            onMarkRead = viewModel::markRead,
            onMarkAllRead = viewModel::markAllRead,
            onDelete = viewModel::delete,
            onDeleteAll = viewModel::requestDeleteAll,
            permissionNotice = { NotificationPermissionNotice(viewModelFactory) },
            modifier = Modifier.padding(innerPadding),
        )
    }

    if (state.confirmDeleteAll) {
        DeleteAllDialog(
            total = state.rows.size,
            unread = state.unreadCount,
            onConfirm = viewModel::confirmDeleteAll,
            onDismiss = viewModel::cancelDeleteAll,
        )
    }
}

@Composable
fun NotificationsContent(
    state: NotificationsUiState,
    onOpen: (NotificationRowUi) -> Unit,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
    permissionNotice: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        permissionNotice()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onMarkAllRead, enabled = state.unreadCount > 0) {
                Text(stringResource(R.string.notifications_mark_all_read))
            }
            TextButton(onClick = onDeleteAll, enabled = state.rows.isNotEmpty()) {
                Text(stringResource(R.string.notifications_delete_all))
            }
        }
        if (state.rows.isEmpty()) {
            Text(
                text = stringResource(R.string.notifications_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.rows, key = { it.id }) { row ->
                NotificationRow(
                    row = row,
                    onOpen = onOpen,
                    onMarkRead = onMarkRead,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun DeleteAllDialog(
    total: Int,
    unread: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notifications_delete_all_title)) },
        text = {
            Text(pluralStringResource(R.plurals.notifications_delete_all_message, total, total, unread))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.notifications_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.notifications_delete_all_cancel))
            }
        },
    )
}
