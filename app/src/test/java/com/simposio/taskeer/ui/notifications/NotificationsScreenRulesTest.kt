package com.simposio.taskeer.ui.notifications

import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsScreenRulesTest {

    @Test
    fun sinSesion_soloDispositivo() {
        assertEquals(listOf(NotificationsTab.DEVICE), NotificationsScreenRules.visibleTabs(SessionState.SignedOut()))
        assertEquals(listOf(NotificationsTab.DEVICE), NotificationsScreenRules.visibleTabs(SessionState.Resuming))
    }

    @Test
    fun conSesion_dosPestanas() {
        val tabs = NotificationsScreenRules.visibleTabs(SessionState.SignedIn(AccountSummary("a@x.com", "A"), offline = true))

        assertEquals(listOf(NotificationsTab.DEVICE, NotificationsTab.ACCOUNT), tabs)
    }

    @Test
    fun marcarTodas_soloConNoLeidas() {
        assertTrue(NotificationsScreenRules.accountActions(unreadCount = 1, readCount = 0).markAllRead)
        assertFalse(NotificationsScreenRules.accountActions(unreadCount = 0, readCount = 3).markAllRead)
    }

    @Test
    fun borrarLeidas_soloConLeidas() {
        assertTrue(NotificationsScreenRules.accountActions(unreadCount = 0, readCount = 1).deleteRead)
        assertFalse(NotificationsScreenRules.accountActions(unreadCount = 4, readCount = 0).deleteRead)
    }
}
