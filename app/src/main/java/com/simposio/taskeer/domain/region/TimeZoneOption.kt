package com.simposio.taskeer.domain.region

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

data class TimeZoneOption(val ianaName: String, val displayName: String) {
    fun zoneId(): ZoneId = ZoneId.of(ianaName)

    fun offsetAt(instant: Instant): ZoneOffset = zoneId().rules.getOffset(instant)
}
