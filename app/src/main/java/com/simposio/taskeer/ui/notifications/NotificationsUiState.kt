package com.simposio.taskeer.ui.notifications

import com.simposio.taskeer.domain.model.AppNotificationKind

data class NotificationRowUi(
    val id: String,
    val kind: AppNotificationKind,
    val taskId: String?,
    val taskTitle: String?,
    val activeTaskCount: Int?,
    val createdAtText: String,
    val unread: Boolean,
    val delivered: Boolean,
)

data class NotificationsUiState(
    val rows: List<NotificationRowUi> = emptyList(),
    val unreadCount: Int = 0,
    val confirmDeleteAll: Boolean = false,
)

sealed interface NotificationsEvent {
    data class OpenTask(val taskId: String) : NotificationsEvent

    data object ShowTaskGone : NotificationsEvent
}
