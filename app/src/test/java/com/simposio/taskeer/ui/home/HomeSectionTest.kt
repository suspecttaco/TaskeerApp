package com.simposio.taskeer.ui.home

import com.simposio.taskeer.ui.navigation.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun calendario_esMarcadorWip() {
        assertTrue(HomeSection.CALENDAR.isPlaceholder)
        assertEquals(AppDestination.CALENDAR, HomeSection.CALENDAR.destination)
        assertEquals(1, HomeSection.entries.count { it.isPlaceholder })
    }

    @Test
    fun cadaSeccion_tieneDestinoDistinto() {
        val destinations = HomeSection.entries.map { it.destination }

        assertEquals(destinations.size, destinations.toSet().size)
        assertFalse(HomeSection.MY_DAY.isPlaceholder)
    }
}
