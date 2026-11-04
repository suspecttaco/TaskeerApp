package com.simposio.taskeer.domain.model

import java.time.Duration

enum class ReminderLead(val offset: Duration) {
    AT_DUE(Duration.ZERO),
    FIFTEEN_MINUTES(Duration.ofMinutes(15)),
    ONE_HOUR(Duration.ofHours(1)),
    ONE_DAY(Duration.ofDays(1)),
    ;

    companion object {
        val Default: ReminderLead = ONE_HOUR

        fun fromStorage(name: String?): ReminderLead =
            entries.firstOrNull { it.name == name } ?: Default
    }
}
