package com.simposio.taskeer.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.NotificationHistoryRepository
import com.simposio.taskeer.data.repository.NotificationInboxRepository
import com.simposio.taskeer.domain.notification.InboxRules
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UnreadCounterViewModel(
    history: NotificationHistoryRepository,
    private val inbox: NotificationInboxRepository,
) : ViewModel() {

    val unreadCount: StateFlow<Int> = combine(
        history.observeUnreadCount(),
        inbox.state.map { it.unreadCount },
        InboxRules::totalUnread,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    // La frecuencia y la sesión las decide el repositorio: sin sesión o dentro del intervalo no hay petición.
    fun onAppForegrounded() {
        viewModelScope.launch { inbox.refreshUnread() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
