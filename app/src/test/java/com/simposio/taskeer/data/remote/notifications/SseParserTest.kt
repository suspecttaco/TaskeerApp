package com.simposio.taskeer.data.remote.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SseParserTest {

    private val parser = SseParser()

    private fun feedAll(vararg lines: String): List<SseEvent> = lines.flatMap { parser.feed(it) }

    @Test
    fun ignoraComentarios() {
        assertEquals(emptyList<SseEvent>(), feedAll(": open", ": heartbeat", ""))
    }

    @Test
    fun despachaEnLineaVacia() {
        assertEquals(emptyList<SseEvent>(), feedAll("event: notification", "data: {}"))

        val events = parser.feed("")

        assertEquals(listOf("notification" to "{}"), events.map { it.name to it.data })
    }

    @Test
    fun unePorLineasDataConSaltos() {
        val events = feedAll("data: a", "data: b", "")

        assertEquals("a\nb", events.single().data)
    }

    @Test
    fun quitaUnEspacioInicial() {
        val events = feedAll("data:  x", "")

        assertEquals(" x", events.single().data)
    }

    @Test
    fun eventoSinDataNoDespacha() {
        assertEquals(emptyList<SseEvent>(), feedAll("event: notification", ""))
    }

    @Test
    fun camposDesconocidosIgnorados() {
        val events = feedAll("id: 7", "retry: 1000", "foo: bar", "data: x", "")

        assertEquals("message" to "x", events.single().let { it.name to it.data })
    }

    @Test
    fun toleraCRSuelto() {
        val events = feedAll("event: notification\rdata: {}\r")

        assertEquals(listOf("notification" to "{}"), events.map { it.name to it.data })
    }

    @Test
    fun descartaEventoMayorDe64KiBYSeResincroniza() {
        val chunk = "x".repeat(16 * 1024)

        val big = feedAll("event: notification", "data: $chunk", "data: $chunk", "data: $chunk", "data: $chunk", "data: $chunk", "")
        val next = feedAll("event: notification", "data: ok", "")

        assertEquals(emptyList<SseEvent>(), big)
        assertEquals("ok", next.single().data)
    }

    @Test
    fun dosEventosSeguidos() {
        val events = feedAll("event: notification", "data: 1", "", "event: notification", "data: 2", "")

        assertEquals(listOf("1", "2"), events.map { it.data })
    }

    @Test
    fun toStringNoIncluyeData() {
        val event = feedAll("data: secreto", "").single()

        assertFalse(event.toString().contains("secreto"))
    }
}
