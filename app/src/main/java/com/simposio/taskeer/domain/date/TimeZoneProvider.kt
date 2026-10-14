package com.simposio.taskeer.domain.date

import java.time.ZoneId

fun interface TimeZoneProvider {
    fun current(): ZoneId
}
