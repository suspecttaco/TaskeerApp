package com.simposio.taskeer.domain.notification

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxRulesTest {

    private val t0 = Instant.parse("2026-11-26T10:00:00Z")

    private fun item(id: String, at: Instant = t0, read: Boolean = false) = ServerNotification(
        id = id,
        type = ServerNotificationType.TASK_ASSIGNED,
        title = "t-$id",
        body = null,
        taskId = null,
        createdAt = at,
        readAt = if (read) at else null,
    )

    @Test
    fun ordena_porFechaDescendenteYIdAscendente() {
        val older = item("a", t0.minusSeconds(60))
        val newerB = item("b", t0)
        val newerA = item("a2", t0)

        val ids = InboxRules.sorted(listOf(older, newerB, newerA)).map { it.id }

        assertEquals(listOf("a2", "b", "a"), ids)
    }

    @Test
    fun unreadCount_cuentaReadAtNulo() {
        val items = listOf(item("1"), item("2", read = true), item("3"))

        assertEquals(2, InboxRules.unreadCount(items))
    }

    @Test
    fun totalUnread_sumaYNuncaNegativo() {
        assertEquals(5, InboxRules.totalUnread(2, 3))
        assertEquals(3, InboxRules.totalUnread(-4, 3))
        assertEquals(0, InboxRules.totalUnread(-1, -1))
    }

    @Test
    fun shouldRefresh_sinIntentoPrevio_esTrue() {
        assertTrue(InboxRules.shouldRefresh(null, t0))
    }

    @Test
    fun shouldRefresh_antesDe30s_esFalse() {
        assertFalse(InboxRules.shouldRefresh(t0, t0.plusSeconds(29)))
    }

    @Test
    fun shouldRefresh_en30s_esTrue() {
        assertTrue(InboxRules.shouldRefresh(t0, t0.plusSeconds(30)))
    }

    @Test
    fun mergeUnread_anadeSoloLasAusentes() {
        val current = listOf(item("1", t0), item("2", t0.minusSeconds(5), read = true))
        val unread = listOf(item("1", t0), item("3", t0.plusSeconds(5)))

        val merged = InboxRules.mergeUnread(current, unread)

        assertEquals(listOf("3", "1", "2"), merged.map { it.id })
        assertTrue(merged.first { it.id == "2" }.readAt != null)
    }

    @Test
    fun reapplyPending_noReinsertaBorradas() {
        val fresh = listOf(item("1"), item("2"))

        val result = InboxRules.reapplyPending(fresh, PendingOps(deletedIds = setOf("1")), t0)

        assertEquals(listOf("2"), result.map { it.id })
    }

    @Test
    fun reapplyPending_noDesmarcaLeidas() {
        val fresh = listOf(item("1"), item("2"))
        val readAt = t0.plusSeconds(9)

        val result = InboxRules.reapplyPending(fresh, PendingOps(readIds = setOf("1")), readAt)

        assertEquals(readAt, result.first { it.id == "1" }.readAt)
        assertTrue(result.first { it.id == "2" }.isUnread)
    }

    @Test
    fun reapplyPending_deleteRead_quitaLasLeidas() {
        val fresh = listOf(item("1"), item("2", read = true))

        val result = InboxRules.reapplyPending(fresh, PendingOps(readDeleted = true), t0)

        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun reapplyPending_sinPendientes_devuelveLaMismaLista() {
        val fresh = listOf(item("1"))

        assertEquals(fresh, InboxRules.reapplyPending(fresh, PendingOps(), t0))
    }
}
