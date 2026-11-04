package com.simposio.taskeer.domain.reminder

import com.simposio.taskeer.domain.model.DigestSettings
import com.simposio.taskeer.domain.model.ReminderCandidate
import com.simposio.taskeer.domain.model.ReminderLead
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

val MISSED_TOLERANCE: Duration = Duration.ofHours(1)

private const val DIGEST_LOOKAHEAD_DAYS = 8L

fun isWithinTolerance(at: Instant, now: Instant): Boolean =
    Duration.between(at, now) < MISSED_TOLERANCE

fun fireAt(dueAt: Instant, lead: ReminderLead): Instant = dueAt.minus(lead.offset)

fun dueDeadlineNotifications(
    candidates: List<ReminderCandidate>,
    lead: ReminderLead,
    checkpoint: Instant,
    now: Instant,
): List<ReminderCandidate> = candidates
    .map { it to fireAt(it.dueAt, lead) }
    .filter { (_, at) -> at.isAfter(checkpoint) && !at.isAfter(now) && isWithinTolerance(at, now) }
    .sortedWith(compareBy({ it.second }, { it.first.taskId }))
    .map { it.first }

fun nextDeadlineFireAt(
    candidates: List<ReminderCandidate>,
    lead: ReminderLead,
    now: Instant,
): Instant? = candidates
    .map { fireAt(it.dueAt, lead) }
    .filter { it.isAfter(now) }
    .minOrNull()

fun digestOccurrences(
    settings: DigestSettings,
    zone: ZoneId,
    after: Instant,
    upTo: Instant,
): List<Instant> {
    if (!settings.enabled || settings.days.isEmpty() || !upTo.isAfter(after)) {
        return emptyList()
    }
    val first = after.atZone(zone).toLocalDate().minusDays(1)
    val last = upTo.atZone(zone).toLocalDate().plusDays(1)
    return generateSequence(first) { it.plusDays(1) }
        .takeWhile { !it.isAfter(last) }
        .filter { it.dayOfWeek in settings.days }
        .map { digestInstant(it, settings, zone) }
        .filter { it.isAfter(after) && !it.isAfter(upTo) }
        .toList()
}

fun dueDigest(
    settings: DigestSettings,
    zone: ZoneId,
    checkpoint: Instant,
    now: Instant,
): List<Instant> {
    val earliest = now.minus(MISSED_TOLERANCE)
    return digestOccurrences(settings, zone, maxOf(checkpoint, earliest), now)
}

fun nextDigestAt(settings: DigestSettings, zone: ZoneId, now: Instant): Instant? =
    digestOccurrences(settings, zone, now, now.plus(Duration.ofDays(DIGEST_LOOKAHEAD_DAYS)))
        .firstOrNull()

private fun digestInstant(date: LocalDate, settings: DigestSettings, zone: ZoneId): Instant =
    ZonedDateTime.of(date, settings.time, zone).toInstant()
