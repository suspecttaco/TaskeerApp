package com.simposio.taskeer.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.NotificationInboxRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.notification.InboxOutcome
import com.simposio.taskeer.domain.notification.InboxState
import com.simposio.taskeer.domain.notification.ServerNotification
import com.simposio.taskeer.ui.tasks.DUE_DATE_TIME_FORMATTER
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountNotificationsViewModel(
    private val inbox: NotificationInboxRepository,
    session: StateFlow<SessionState>,
    private val tasks: TaskRepository,
    private val dateTimeConverter: DateTimeConverter,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<InboxErrorMessage?>(null)
    private val confirmDeleteRead = MutableStateFlow(false)
    private var opened = false

    // Canal y no estado: abrir el editor o mostrar el aviso ocurre una vez y no debe repetirse al rotar.
    private val eventChannel = Channel<AccountNotificationsEvent>(Channel.BUFFERED)

    val events: Flow<AccountNotificationsEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<AccountNotificationsUiState> = combine(
        inbox.state,
        session,
        refreshing,
        error,
        confirmDeleteRead,
    ) { inboxState, sessionState, isRefreshing, errorMessage, confirm ->
        inboxState.toUi(
            tabs = NotificationsScreenRules.visibleTabs(sessionState),
            refreshing = isRefreshing,
            error = errorMessage,
            confirm = confirm,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AccountNotificationsUiState())

    fun onTabOpened() {
        if (opened) return
        opened = true
        refresh()
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            error.value = null
            try {
                val outcome = inbox.refresh()
                if (outcome is InboxOutcome.Failed) reportRefreshFailure(outcome)
            } finally {
                refreshing.value = false
            }
        }
    }

    private suspend fun reportRefreshFailure(failure: InboxOutcome.Failed) {
        val message = InboxErrorRules.messageFor(failure.error, failure.retryAfterSeconds)
        if (inbox.state.value.listLoaded) {
            message?.let { eventChannel.send(AccountNotificationsEvent.ShowError(it)) }
        } else {
            error.value = message ?: InboxErrorMessage.Generic
        }
    }

    fun onOpen(row: AccountRowUi) {
        viewModelScope.launch {
            if (row.unread) report(inbox.markRead(row.id))
            val taskId = row.taskId ?: return@launch
            when (tasks.getTask(taskId)) {
                is DomainResult.Success -> eventChannel.send(AccountNotificationsEvent.OpenTask(taskId))
                is DomainResult.Failure -> eventChannel.send(AccountNotificationsEvent.ShowTaskGone)
            }
        }
    }

    fun markRead(id: String) {
        viewModelScope.launch { report(inbox.markRead(id)) }
    }

    fun markAllRead() {
        viewModelScope.launch { report(inbox.markAllRead()) }
    }

    fun delete(id: String) {
        viewModelScope.launch { report(inbox.delete(id)) }
    }

    fun requestDeleteRead() {
        if (inbox.state.value.items.any { !it.isUnread }) confirmDeleteRead.value = true
    }

    fun cancelDeleteRead() {
        confirmDeleteRead.value = false
    }

    fun confirmDeleteRead() {
        confirmDeleteRead.value = false
        viewModelScope.launch { report(inbox.deleteRead()) }
    }

    private suspend fun report(outcome: InboxOutcome) {
        if (outcome !is InboxOutcome.Failed) return
        InboxErrorRules.messageFor(outcome.error, outcome.retryAfterSeconds)
            ?.let { eventChannel.send(AccountNotificationsEvent.ShowError(it)) }
    }

    private fun InboxState.toUi(
        tabs: List<NotificationsTab>,
        refreshing: Boolean,
        error: InboxErrorMessage?,
        confirm: Boolean,
    ): AccountNotificationsUiState {
        val readCount = items.count { !it.isUnread }
        val phase = when {
            listLoaded -> if (items.isEmpty()) AccountPhase.EMPTY else AccountPhase.LIST
            refreshing -> AccountPhase.LOADING
            error != null -> AccountPhase.ERROR
            else -> AccountPhase.LOADING
        }
        return AccountNotificationsUiState(
            tabs = tabs,
            phase = phase,
            rows = items.map { it.toRow() },
            unreadCount = unreadCount,
            readCount = readCount,
            refreshing = refreshing && listLoaded,
            error = error,
            confirmDeleteRead = confirm && readCount > 0,
        )
    }

    private fun ServerNotification.toRow(): AccountRowUi = AccountRowUi(
        id = id,
        type = type,
        title = title,
        body = body,
        taskId = taskId,
        createdAtText = dateTimeConverter.toLocalDateTime(createdAt).format(DUE_DATE_TIME_FORMATTER),
        unread = isUnread,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
