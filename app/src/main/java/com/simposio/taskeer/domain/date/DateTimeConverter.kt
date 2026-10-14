package com.simposio.taskeer.domain.date

import java.time.Instant
import java.time.LocalDateTime

class DateTimeConverter(private val timeZoneProvider: TimeZoneProvider) {

    fun toLocalDateTime(utc: Instant): LocalDateTime =
        LocalDateTime.ofInstant(utc, timeZoneProvider.current())

    fun toInstant(localDateTime: LocalDateTime): Instant =
        localDateTime.atZone(timeZoneProvider.current()).toInstant()
}
