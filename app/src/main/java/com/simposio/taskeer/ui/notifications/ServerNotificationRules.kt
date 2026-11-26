package com.simposio.taskeer.ui.notifications

import androidx.annotation.StringRes
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.notification.ServerNotificationType

enum class InboxIcon { ALARM, ASSIGNMENT, ROLE, SECURITY, INVITATION, MENTION, GENERIC }

object ServerNotificationRules {

    @StringRes
    fun labelRes(type: ServerNotificationType): Int = when (type) {
        ServerNotificationType.REMINDER -> R.string.inbox_type_reminder
        ServerNotificationType.TASK_ASSIGNED -> R.string.inbox_type_task_assigned
        ServerNotificationType.ROLE_CHANGED -> R.string.inbox_type_role_changed
        ServerNotificationType.RECOVERY_CODE_USED -> R.string.inbox_type_security
        ServerNotificationType.INVITATION -> R.string.inbox_type_invitation
        ServerNotificationType.MENTION -> R.string.inbox_type_mention
        ServerNotificationType.UNKNOWN -> R.string.inbox_type_unknown
    }

    fun icon(type: ServerNotificationType): InboxIcon = when (type) {
        ServerNotificationType.REMINDER -> InboxIcon.ALARM
        ServerNotificationType.TASK_ASSIGNED -> InboxIcon.ASSIGNMENT
        ServerNotificationType.ROLE_CHANGED -> InboxIcon.ROLE
        ServerNotificationType.RECOVERY_CODE_USED -> InboxIcon.SECURITY
        ServerNotificationType.INVITATION -> InboxIcon.INVITATION
        ServerNotificationType.MENTION -> InboxIcon.MENTION
        ServerNotificationType.UNKNOWN -> InboxIcon.GENERIC
    }

    // Un código de recuperación usado es un aviso de seguridad de la cuenta: se destaca con el color de error.
    fun highlightsSecurity(type: ServerNotificationType): Boolean = type == ServerNotificationType.RECOVERY_CODE_USED
}
