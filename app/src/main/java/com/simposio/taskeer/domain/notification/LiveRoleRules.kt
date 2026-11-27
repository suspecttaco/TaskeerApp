package com.simposio.taskeer.domain.notification

object LiveRoleRules {
    fun isRoleChange(notification: ServerNotification): Boolean =
        notification.type == ServerNotificationType.ROLE_CHANGED

    // El servidor no reenvía lo perdido mientras el canal está caído: volver de WAITING obliga a releer.
    fun shouldRefreshOnState(previous: LiveChannelState, current: LiveChannelState): Boolean =
        previous == LiveChannelState.WAITING && current == LiveChannelState.CONNECTED
}
