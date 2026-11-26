package com.simposio.taskeer.data.remote.notifications

import com.simposio.taskeer.domain.notification.ServerNotificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationLiveContractTest {

    private val ticket = "a".repeat(32) + "0".repeat(32)
    private val id = "00000000-0000-4000-8000-000000000001"

    private fun json(type: String = "MENTION", id: String? = this.id, title: String? = "Hola") = buildString {
        append("{")
        if (id != null) append("\"id\":\"$id\",")
        if (title != null) append("\"title\":\"$title\",")
        append("\"type\":\"$type\",\"body\":null,\"readAt\":null,\"createdAt\":\"2026-11-26T09:00:00Z\"}")
    }

    @Test
    fun parseTicket_valido() {
        assertEquals(ticket, NotificationsApiContract.parseTicket("""{"ticket":"$ticket"}"""))
    }

    @Test
    fun parseTicket_noHexOLongitudIncorrecta_esNull() {
        assertNull(NotificationsApiContract.parseTicket("""{"ticket":"${"A".repeat(64)}"}"""))
        assertNull(NotificationsApiContract.parseTicket("""{"ticket":"${"a".repeat(63)}"}"""))
        assertNull(NotificationsApiContract.parseTicket("""{"ticket":"${"a".repeat(63)}/"}"""))
        assertNull(NotificationsApiContract.parseTicket("""{"ticket":null}"""))
    }

    @Test
    fun parseTicket_cuerpoIlegible_esNull() {
        assertNull(NotificationsApiContract.parseTicket("no json"))
        assertNull(NotificationsApiContract.parseTicket(""))
    }

    @Test
    fun parseOne_valido() {
        val parsed = NotificationsApiContract.parseOne(json())

        assertEquals(id, parsed?.id)
        assertEquals(ServerNotificationType.MENTION, parsed?.type)
        assertEquals(true, parsed?.isUnread)
    }

    @Test
    fun parseOne_tipoDesconocido_esUnknown() {
        assertEquals(ServerNotificationType.UNKNOWN, NotificationsApiContract.parseOne(json(type = "NUEVO"))?.type)
    }

    @Test
    fun parseOne_sinIdOTitulo_esNull() {
        assertNull(NotificationsApiContract.parseOne(json(id = null)))
        assertNull(NotificationsApiContract.parseOne(json(title = null)))
    }

    @Test
    fun parseOne_jsonRoto_esNull() {
        assertNull(NotificationsApiContract.parseOne("{\"id\":"))
    }
}
