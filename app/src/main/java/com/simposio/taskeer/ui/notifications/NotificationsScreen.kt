package com.simposio.taskeer.ui.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.notification.badgeLabel
import com.simposio.taskeer.ui.components.NoticeKind
import com.simposio.taskeer.ui.components.showNotice
import com.simposio.taskeer.ui.permission.NotificationPermissionNotice
import com.simposio.taskeer.ui.tasks.DueDatePickerDialog
import com.simposio.taskeer.ui.tasks.DueTimePickerDialog
import com.simposio.taskeer.ui.tasks.rememberTaskEditorHost
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
    val accountViewModel: AccountNotificationsViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accountState by accountViewModel.uiState.collectAsStateWithLifecycle()
    val editorHost = rememberTaskEditorHost(viewModelFactory, onDelete)
    val snackbarHostState = remember { SnackbarHostState() }
    var customRow by remember { mutableStateOf<NotificationRowUi?>(null) }
    var customDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedName by rememberSaveable { mutableStateOf(NotificationsTab.DEVICE.name) }
    val tab = NotificationsTab.valueOf(selectedName).takeIf { it in accountState.tabs } ?: NotificationsTab.DEVICE
    val taskGoneMessage = stringResource(R.string.notifications_task_gone)
    val taskMissingMessage = stringResource(R.string.inbox_task_missing)
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is NotificationsEvent.OpenTask -> editorHost.openForEdit(event.taskId)
                NotificationsEvent.ShowTaskGone -> snackbarHostState.showNotice(taskGoneMessage, NoticeKind.INFO)
                is NotificationsEvent.ShowError -> snackbarHostState.showNotice(event.message, NoticeKind.ERROR)
            }
        }
    }
    LaunchedEffect(accountViewModel) {
        accountViewModel.events.collect { event ->
            when (event) {
                is AccountNotificationsEvent.OpenTask -> editorHost.openForEdit(event.taskId)
                AccountNotificationsEvent.ShowTaskGone -> snackbarHostState.showNotice(taskMissingMessage, NoticeKind.INFO)
                is AccountNotificationsEvent.ShowError -> snackbarHostState.showNotice(event.message.text(resources), NoticeKind.ERROR)
            }
        }
    }
    LaunchedEffect(tab) {
        if (tab == NotificationsTab.ACCOUNT) accountViewModel.onTabOpened()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (accountState.tabs.size > 1) {
                NotificationsTabs(
                    selected = tab,
                    tabs = accountState.tabs,
                    deviceUnread = state.unreadCount,
                    accountUnread = accountState.unreadCount,
                    onSelect = { selectedName = it.name },
                )
            }
            when (tab) {
                NotificationsTab.DEVICE -> DeviceNotificationsContent(
                    state = state,
                    onOpen = viewModel::onOpen,
                    onMarkRead = viewModel::markRead,
                    onMarkAllRead = viewModel::markAllRead,
                    onDelete = viewModel::delete,
                    onDeleteAll = viewModel::requestDeleteAll,
                    onSnooze = viewModel::snooze,
                    onSnoozeCustom = { customRow = it },
                    permissionNotice = { NotificationPermissionNotice(viewModelFactory) },
                )
                NotificationsTab.ACCOUNT -> AccountNotificationsContent(
                    state = accountState,
                    onRefresh = accountViewModel::refresh,
                    onOpen = accountViewModel::onOpen,
                    onMarkRead = accountViewModel::markRead,
                    onMarkAllRead = accountViewModel::markAllRead,
                    onDelete = accountViewModel::delete,
                    onRequestDeleteRead = accountViewModel::requestDeleteRead,
                    onConfirmDeleteRead = accountViewModel::confirmDeleteRead,
                    onCancelDeleteRead = accountViewModel::cancelDeleteRead,
                )
            }
        }
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
private fun NotificationsTabs(
    selected: NotificationsTab,
    tabs: List<NotificationsTab>,
    deviceUnread: Int,
    accountUnread: Int,
    onSelect: (NotificationsTab) -> Unit,
) {
    SecondaryTabRow(selectedTabIndex = tabs.indexOf(selected).coerceAtLeast(0)) {
        tabs.forEach { tab ->
            val label = badgeLabel(if (tab == NotificationsTab.DEVICE) deviceUnread else accountUnread)
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = {
                    BadgedBox(badge = { if (label != null) Badge { Text(label) } }) {
                        Text(
                            stringResource(
                                if (tab == NotificationsTab.DEVICE) R.string.inbox_tab_device else R.string.inbox_tab_account,
                            ),
                        )
                    }
                },
            )
        }
    }
}
