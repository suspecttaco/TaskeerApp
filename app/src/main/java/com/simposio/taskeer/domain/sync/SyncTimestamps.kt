package com.simposio.taskeer.domain.sync

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// El contrato exige milisegundos y «Z» siempre; Instant.toString() omite los ceros finales.
object SyncTimestamps {
    private val FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    fun format(instant: Instant): String = FORMAT.format(instant)

    fun parse(text: String): Instant? = runCatching { Instant.parse(text) }.getOrNull()
}
