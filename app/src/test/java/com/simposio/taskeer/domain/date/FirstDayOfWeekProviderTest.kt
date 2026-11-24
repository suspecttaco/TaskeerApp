package com.simposio.taskeer.domain.date

import java.time.DayOfWeek
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FirstDayOfWeekProviderTest {

    @Test
    fun dayOfWeekFromCalendar_mapeaDomingoASabado() {
        val expected = listOf(
            DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
        )

        assertEquals(expected, (1..7).map(::dayOfWeekFromCalendar))
    }

    @Test
    fun dayOfWeekFromCalendar_fueraDeRango_falla() {
        assertThrows(IllegalArgumentException::class.java) { dayOfWeekFromCalendar(0) }
        assertThrows(IllegalArgumentException::class.java) { dayOfWeekFromCalendar(8) }
    }

    @Test
    fun system_usaLaRegionDelLocale() {
        assertEquals(DayOfWeek.SUNDAY, SystemFirstDayOfWeekProvider { Locale.US }.current())
        assertEquals(DayOfWeek.MONDAY, SystemFirstDayOfWeekProvider { Locale.forLanguageTag("es-ES") }.current())
    }
}
