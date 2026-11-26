package com.simposio.taskeer.ui.notifications

import com.simposio.taskeer.domain.notification.ServerNotificationType

data class AccountRowUi(
    val id: String,
    val type: ServerNotificationType,
    val title: String,
    val body: String?,
    val taskId: String?,
    val createdAtText: String,
    val unread: Boolean,
)

enum class AccountPhase { LOADING, LIST, EMPTY, ERROR }

data class AccountNotificationsUiState(
    val tabs: List<NotificationsTab> = listOf(NotificationsTab.DEVICE),
    val phase: AccountPhase = AccountPhase.LOADING,
    val rows: List<AccountRowUi> = emptyList(),
    val unreadCount: Int = 0,
    val readCount: Int = 0,
    val refreshing: Boolean = false,
    val error: InboxErrorMessage? = null,
    val confirmDeleteRead: Boolean = false,
)

sealed interface AccountNotificationsEvent {
    data class OpenTask(val taskId: String) : AccountNotificationsEvent

    data object ShowTaskGone : AccountNotificationsEvent

    data class ShowError(val message: InboxErrorMessage) : AccountNotificationsEvent
}
