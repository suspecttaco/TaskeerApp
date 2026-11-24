package com.simposio.taskeer.domain.reminder

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

enum class SnoozeOption { LATER_TODAY, TOMORROW, NEXT_WEEK }

fun SnoozeOption.resolve(now: Instant, zone: ZoneId, defaultTime: LocalTime): Instant = when (this) {
    SnoozeOption.LATER_TODAY -> laterToday(now)
    SnoozeOption.TOMORROW -> tomorrowAt(now, zone, defaultTime)
    SnoozeOption.NEXT_WEEK -> nextWeekAt(now, zone, defaultTime)
}
