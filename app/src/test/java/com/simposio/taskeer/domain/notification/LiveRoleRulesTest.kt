package com.simposio.taskeer.domain.notification

import com.simposio.taskeer.domain.workspace.BoardRoleRules
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRoleRulesTest {

    private fun notification(type: ServerNotificationType) = ServerNotification(
        id = "n1",
        type = type,
        title = "t",
        body = null,
        taskId = null,
        createdAt = Instant.parse("2026-11-01T10:00:00Z"),
        readAt = null,
    )

    @Test
    fun isRoleChange_soloRoleChanged() {
        ServerNotificationType.entries.forEach {
            assertEquals(it.name, it == ServerNotificationType.ROLE_CHANGED, LiveRoleRules.isRoleChange(notification(it)))
        }
    }

    @Test
    fun reconexion_desdeWaitingAConnected_si() {
        assertTrue(LiveRoleRules.shouldRefreshOnState(LiveChannelState.WAITING, LiveChannelState.CONNECTED))
    }

    @Test
    fun primeraConexion_no() {
        assertFalse(LiveRoleRules.shouldRefreshOnState(LiveChannelState.CONNECTING, LiveChannelState.CONNECTED))
        assertFalse(LiveRoleRules.shouldRefreshOnState(LiveChannelState.STOPPED, LiveChannelState.CONNECTED))
    }

    @Test
    fun otrasTransiciones_no() {
        val states = LiveChannelState.entries
        val refreshing = states.flatMap { from -> states.map { to -> from to to } }
            .filter { (from, to) -> LiveRoleRules.shouldRefreshOnState(from, to) }

        assertEquals(listOf(LiveChannelState.WAITING to LiveChannelState.CONNECTED), refreshing)
    }

    @Test
    fun pausaMinima_esUnSegundo() {
        assertEquals(1000L, BoardRoleRules.SIGNAL_REFRESH_PAUSE_MILLIS)
    }
}
