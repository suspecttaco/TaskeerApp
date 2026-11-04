package com.simposio.taskeer.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

data class DeadlineReminderSettings(
    val enabled: Boolean = false,
    val lead: ReminderLead = ReminderLead.Default,
)

data class DigestSettings(
    val enabled: Boolean = false,
    val days: Set<DayOfWeek> = WEEKDAYS,
    val time: LocalTime = DEFAULT_REMINDER_TIME,
) {
    companion object {
        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
    }
}

val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)
