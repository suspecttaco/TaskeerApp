package com.simposio.taskeer.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.NotificationHistoryRepository
import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.data.repository.ReminderRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.AppNotification
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.reminder.SnoozeOption
import com.simposio.taskeer.domain.reminder.resolve
import com.simposio.taskeer.ui.tasks.DUE_DATE_TIME_FORMATTER
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationsViewModel(
    private val history: NotificationHistoryRepository,
    private val tasks: TaskRepository,
    private val dateTimeConverter: DateTimeConverter,
    private val reminders: ReminderRepository,
    private val preferences: PreferencesRepository,
    private val timeZone: TimeZoneProvider,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {

    // Un gesto en vuelo por fila: el doble toque no lanza dos posponer antes de que la entrada se marque.
    private val snoozing = mutableSetOf<String>()

    private val confirmDeleteAll = MutableStateFlow(false)

    // Canal y no estado: abrir el editor o mostrar el aviso ocurre una vez y no debe repetirse al rotar.
    private val eventChannel = Channel<NotificationsEvent>(Channel.BUFFERED)

    val events: Flow<NotificationsEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<NotificationsUiState> = combine(
        history.observeAll(),
        confirmDeleteAll,
    ) { entries, confirm ->
        NotificationsUiState(
            rows = entries.map { it.toRow() },
            unreadCount = entries.count { it.isUnread },
            confirmDeleteAll = confirm && entries.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), NotificationsUiState())

    fun onOpen(row: NotificationRowUi) {
        viewModelScope.launch {
            history.markRead(row.id)
            val taskId = row.taskId ?: return@launch
            when (tasks.getTask(taskId)) {
                is DomainResult.Success -> eventChannel.send(NotificationsEvent.OpenTask(taskId))
                is DomainResult.Failure -> eventChannel.send(NotificationsEvent.ShowTaskGone)
            }
        }
    }

    fun markRead(id: String) {
        viewModelScope.launch { history.markRead(id) }
    }

    fun snooze(row: NotificationRowUi, option: SnoozeOption) {
        snoozeWith(row) { option.resolve(clock(), timeZone.current(), preferences.defaultReminderTime.first()) }
    }

    fun snoozeAt(row: NotificationRowUi, local: LocalDateTime) {
        snoozeWith(row) { dateTimeConverter.toInstant(local) }
    }

    private fun snoozeWith(row: NotificationRowUi, remindAt: suspend () -> Instant) {
        val reminderId = row.reminderId
        if (!row.canSnooze || reminderId == null || !snoozing.add(row.id)) return
        viewModelScope.launch {
            try {
                val at = remindAt()
                when (val result = reminders.snoozeReminder(reminderId, at)) {
                    is DomainResult.Success -> history.markSnoozed(row.id, at)
                    is DomainResult.Failure ->
                        eventChannel.send(NotificationsEvent.ShowError(result.error.message))
                }
            } finally {
                snoozing.remove(row.id)
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch { history.markAllRead() }
    }

    fun delete(id: String) {
        viewModelScope.launch { history.delete(id) }
    }

    fun requestDeleteAll() {
        viewModelScope.launch {
            if (history.observeAll().first().isNotEmpty()) {
                confirmDeleteAll.value = true
            }
        }
    }

    fun cancelDeleteAll() {
        confirmDeleteAll.value = false
    }

    fun confirmDeleteAll() {
        confirmDeleteAll.value = false
        viewModelScope.launch { history.deleteAll() }
    }

    private fun AppNotification.toRow(): NotificationRowUi = NotificationRowUi(
        id = id,
        kind = kind,
        taskId = taskId,
        taskTitle = taskTitle,
        activeTaskCount = activeTaskCount,
        createdAtText = dateTimeConverter.toLocalDateTime(createdAt).format(DUE_DATE_TIME_FORMATTER),
        unread = isUnread,
        delivered = delivered,
        reminderId = reminderId,
        canSnooze = canSnooze,
        snoozedUntilText = snoozedUntil?.let {
            dateTimeConverter.toLocalDateTime(it).format(DUE_DATE_TIME_FORMATTER)
        },
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
