package com.simposio.taskeer.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDestinationTest {

    @Test
    fun startEsHome() {
        assertEquals(AppDestination.HOME, AppDestination.START)
        assertEquals("home", AppDestination.START.route)
    }

    @Test
    fun rutasUnicas() {
        val routes = AppDestination.entries.map { it.route }

        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun seccionesPrincipalesPresentes() {
        assertEquals(
            listOf(
                AppDestination.HOME,
                AppDestination.MY_DAY,
                AppDestination.WORKSPACES,
                AppDestination.SETTINGS,
            ),
            AppDestination.topLevel,
        )
        assertTrue(AppDestination.START in AppDestination.topLevel)
    }

    @Test
    fun fromRoute_resuelveElDestino() {
        assertEquals(AppDestination.MY_DAY, AppDestination.fromRoute("my_day"))
        assertNull(AppDestination.fromRoute("desconocido"))
    }
}
