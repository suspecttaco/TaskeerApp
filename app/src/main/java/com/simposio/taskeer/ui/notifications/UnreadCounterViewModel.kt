package com.simposio.taskeer.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.NotificationHistoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class UnreadCounterViewModel(history: NotificationHistoryRepository) : ViewModel() {

    val unreadCount: StateFlow<Int> = history.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
