package com.simposio.taskeer.ui.home

import com.simposio.taskeer.ui.navigation.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSectionTest {

    @Test
    fun orden_esMiDiaProximosTodasCalendario() {
        assertEquals(
            listOf(
                HomeSection.MY_DAY,
                HomeSection.UPCOMING_7_DAYS,
                HomeSection.ALL_TASKS,
                HomeSection.CALENDAR,
            ),
            HomeSection.entries,
        )
    }

    @Test
    fun ningunaSeccionEsMarcador() {
        assertEquals(AppDestination.CALENDAR, HomeSection.CALENDAR.destination)
        assertTrue(HomeSection::class.java.declaredFields.none { it.name == "isPlaceholder" })
    }

    @Test
    fun cadaSeccion_tieneDestinoDistinto() {
        val destinations = HomeSection.entries.map { it.destination }

        assertEquals(destinations.size, destinations.toSet().size)
    }
}
