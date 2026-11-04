package com.simposio.taskeer.domain.reminder

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private val LATER_TODAY_OFFSET: Duration = Duration.ofHours(5)
private const val NEXT_WEEK_DAYS = 7L

fun laterToday(now: Instant): Instant = now.plus(LATER_TODAY_OFFSET)

fun tomorrowAt(now: Instant, zone: ZoneId, time: LocalTime): Instant =
    atLocalDayOffset(now, zone, time, 1)

fun nextWeekAt(now: Instant, zone: ZoneId, time: LocalTime): Instant =
    atLocalDayOffset(now, zone, time, NEXT_WEEK_DAYS)

// ZonedDateTime.of resuelve el hueco de verano hacia delante y el solape con el primer offset.
private fun atLocalDayOffset(now: Instant, zone: ZoneId, time: LocalTime, days: Long): Instant =
    ZonedDateTime.of(now.atZone(zone).toLocalDate().plusDays(days), time, zone).toInstant()
