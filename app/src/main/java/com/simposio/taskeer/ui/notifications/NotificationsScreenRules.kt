package com.simposio.taskeer.ui.notifications

import com.simposio.taskeer.domain.auth.SessionState

enum class NotificationsTab { DEVICE, ACCOUNT }

data class AccountActions(val markAllRead: Boolean, val deleteRead: Boolean)

object NotificationsScreenRules {

    fun visibleTabs(session: SessionState): List<NotificationsTab> =
        if (session is SessionState.SignedIn) listOf(NotificationsTab.DEVICE, NotificationsTab.ACCOUNT) else listOf(NotificationsTab.DEVICE)

    fun accountActions(unreadCount: Int, readCount: Int): AccountActions =
        AccountActions(markAllRead = unreadCount > 0, deleteRead = readCount > 0)
}
