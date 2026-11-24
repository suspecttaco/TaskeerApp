package com.simposio.taskeer.domain.date

import java.time.DayOfWeek
import java.util.Calendar
import java.util.Locale

fun interface FirstDayOfWeekProvider {
    fun current(): DayOfWeek
}

class SystemFirstDayOfWeekProvider(
    private val locale: () -> Locale = { Locale.getDefault() },
) : FirstDayOfWeekProvider {
    override fun current(): DayOfWeek = dayOfWeekFromCalendar(Calendar.getInstance(locale()).firstDayOfWeek)
}

// java.util.Calendar numera de domingo (1) a sábado (7); java.time, de lunes (1) a domingo (7).
fun dayOfWeekFromCalendar(calendarDay: Int): DayOfWeek {
    require(calendarDay in Calendar.SUNDAY..Calendar.SATURDAY) { "Día de Calendar fuera de rango: $calendarDay" }
    return if (calendarDay == Calendar.SUNDAY) DayOfWeek.SUNDAY else DayOfWeek.of(calendarDay - 1)
}
