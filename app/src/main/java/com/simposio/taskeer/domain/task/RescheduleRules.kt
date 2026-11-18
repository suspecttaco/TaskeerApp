package com.simposio.taskeer.domain.task

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class RescheduleTarget { TOMORROW, NEXT_WEEK, SOMEDAY }

val DEFAULT_DUE_TIME: LocalTime = LocalTime.of(9, 0)

// La hora local previa se conserva: el paso a UTC lo resuelve DateTimeConverter, que absorbe los saltos de horario.
fun rescheduledDue(current: LocalDateTime?, target: RescheduleTarget, today: LocalDate): LocalDateTime? {
    val date = when (target) {
        RescheduleTarget.TOMORROW -> today.plusDays(1)
        RescheduleTarget.NEXT_WEEK -> today.plusDays(NEXT_WEEK_DAYS)
        RescheduleTarget.SOMEDAY -> return null
    }
    return date.atTime(current?.toLocalTime() ?: DEFAULT_DUE_TIME)
}

fun dueForQuickAdd(date: LocalDate?, time: LocalTime?): LocalDateTime? =
    date?.atTime(time ?: DEFAULT_DUE_TIME)

private const val NEXT_WEEK_DAYS = 7L
