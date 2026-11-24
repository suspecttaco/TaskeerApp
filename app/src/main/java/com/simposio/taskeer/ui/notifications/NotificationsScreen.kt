package com.simposio.taskeer.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.reminder.SnoozeOption
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.AppDivider
import com.simposio.taskeer.ui.permission.NotificationPermissionNotice
import com.simposio.taskeer.ui.tasks.DueDatePickerDialog
import com.simposio.taskeer.ui.tasks.DueTimePickerDialog
import com.simposio.taskeer.ui.tasks.rememberTaskEditorHost
import com.simposio.taskeer.ui.theme.Spacing
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun NotificationsScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: NotificationsViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editorHost = rememberTaskEditorHost(viewModelFactory, onDelete)
    val snackbarHostState = remember { SnackbarHostState() }
    var customRow by remember { mutableStateOf<NotificationRowUi?>(null) }
    var customDate by remember { mutableStateOf<LocalDate?>(null) }
    val taskGoneMessage = stringResource(R.string.notifications_task_gone)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is NotificationsEvent.OpenTask -> editorHost.openForEdit(event.taskId)
                NotificationsEvent.ShowTaskGone -> snackbarHostState.showSnackbar(taskGoneMessage)
                is NotificationsEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
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
            onSnooze = viewModel::snooze,
            onSnoozeCustom = { customRow = it },
            permissionNotice = { NotificationPermissionNotice(viewModelFactory) },
            modifier = Modifier.padding(innerPadding),
        )
    }

    val pickingRow = customRow
    if (pickingRow != null) {
        val pickedDate = customDate
        if (pickedDate == null) {
            DueDatePickerDialog(
                initialDate = null,
                onDatePicked = { customDate = it },
                onDismiss = { customRow = null },
            )
        } else {
            DueTimePickerDialog(
                initialTime = LocalTime.now().withSecond(0).withNano(0),
                onTimePicked = { time ->
                    viewModel.snoozeAt(pickingRow, LocalDateTime.of(pickedDate, time))
                    customRow = null
                    customDate = null
                },
                onDismiss = {
                    customRow = null
                    customDate = null
                },
            )
        }
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
    onSnooze: (NotificationRowUi, SnoozeOption) -> Unit,
    onSnoozeCustom: (NotificationRowUi) -> Unit,
    permissionNotice: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
    ) {
        permissionNotice()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            ActionIcon(
                icon = Icons.Filled.DoneAll,
                contentDescription = stringResource(R.string.notifications_mark_all_read),
                onClick = onMarkAllRead,
                enabled = state.unreadCount > 0,
            )
            ActionIcon(
                icon = Icons.Filled.DeleteSweep,
                contentDescription = stringResource(R.string.notifications_delete_all),
                onClick = onDeleteAll,
                enabled = state.rows.isNotEmpty(),
            )
        }
        if (state.rows.isEmpty()) {
            Text(
                text = stringResource(R.string.notifications_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LazyColumn {
            items(state.rows, key = { it.id }) { row ->
                NotificationRow(
                    row = row,
                    onOpen = onOpen,
                    onMarkRead = onMarkRead,
                    onDelete = onDelete,
                    onSnooze = onSnooze,
                    onSnoozeCustom = onSnoozeCustom,
                )
                AppDivider()
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
