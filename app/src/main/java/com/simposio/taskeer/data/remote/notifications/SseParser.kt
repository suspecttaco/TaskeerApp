package com.simposio.taskeer.data.remote.notifications

internal class SseEvent(val name: String, val data: String) {
    // `data` lleva el contenido de la notificación: no sale en trazas.
    override fun toString(): String = "SseEvent(name=$name)"
}

// `readUtf8Line` ya quita \n y \r\n; un \r suelto dentro de la línea se trata como otro fin de línea.
internal class SseParser {
    private val data = StringBuilder()
    private var hasData = false
    private var name: String? = null
    private var size = 0
    private var discarding = false

    fun feed(line: String): List<SseEvent> {
        if (!line.contains('\r')) return listOfNotNull(feedLine(line))
        return line.split('\r').mapNotNull { feedLine(it) }
    }

    fun reset() {
        clear()
        discarding = false
    }

    private fun feedLine(line: String): SseEvent? {
        if (line.isEmpty()) return dispatch()
        if (discarding || line.startsWith(':')) return null
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        val value = if (colon < 0) "" else line.substring(colon + 1).removePrefix(" ")
        when (field) {
            "event" -> name = value
            "data" -> append(value)
        }
        return null
    }

    private fun append(value: String) {
        size += value.length + 1
        if (size > MAX_EVENT_CHARS) {
            clear()
            discarding = true
            return
        }
        if (hasData) data.append('\n')
        data.append(value)
        hasData = true
    }

    private fun dispatch(): SseEvent? {
        val wasDiscarding = discarding
        val event = if (!wasDiscarding && hasData) SseEvent(name ?: DEFAULT_EVENT, data.toString()) else null
        clear()
        discarding = false
        return event
    }

    private fun clear() {
        data.setLength(0)
        hasData = false
        name = null
        size = 0
    }

    private companion object {
        const val MAX_EVENT_CHARS = 64 * 1024
        const val DEFAULT_EVENT = "message"
    }
}
