package com.simposio.taskeer.ui.navigation

import com.simposio.taskeer.ui.home.HomeSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDestinationTest {

    @Test
    fun startEsMiDia() {
        assertEquals(AppDestination.MY_DAY, AppDestination.START)
        assertEquals("my_day", AppDestination.START.route)
    }

    @Test
    fun start_esMiDia() {
        assertEquals("my_day", AppDestination.START.route)
        assertEquals(AppDestination.MY_DAY, AppDestination.fromRoute("my_day"))
    }

    @Test
    fun rutasUnicas() {
        val routes = AppDestination.entries.map { it.route }

        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun destinosDelFlujoHomePresentes() {
        assertEquals(
            mapOf(
                "MY_DAY" to "my_day",
                "HOME" to "home",
                "UPCOMING_7_DAYS" to "upcoming_7_days",
                "ALL_TASKS" to "all_tasks",
                "CALENDAR" to "calendar",
                "LIST" to "list/{listId}",
                "BOARD" to "board/{boardId}",
                "BOARD_COLUMNS" to "board_columns/{boardId}",
                "NOTIFICATIONS" to "notifications",
                "SETTINGS" to "settings",
            ),
            AppDestination.entries.associate { it.name to it.route },
        )
    }

    @Test
    fun inboxWorkspacesYPlannedYaNoExisten() {
        val names = AppDestination.entries.map { it.name }

        assertTrue(names.none { it == "INBOX" || it == "WORKSPACES" || it == "PLANNED" })
        assertTrue(AppDestination.Companion::class.java.declaredMethods.none { it.name.contains("topLevel") })
    }

    @Test
    fun listRoute_llevaLaLista() {
        assertEquals("list/compras", AppDestination.listRoute("compras"))
    }

    @Test
    fun fromRoute_resuelveElDestino() {
        AppDestination.entries.forEach { destination ->
            assertEquals(destination, AppDestination.fromRoute(destination.route))
        }
        assertNull(AppDestination.fromRoute("desconocido"))
    }

    @Test
    fun boardColumnsNoEsSeccion() {
        assertEquals("board_columns/{boardId}", AppDestination.BOARD_COLUMNS.route)
        assertFalse(HomeSection.entries.any { it.destination == AppDestination.BOARD_COLUMNS })
        assertEquals("board_columns/tablero-1", AppDestination.boardColumnsRoute("tablero-1"))
    }

    @Test
    fun boardRoute_llevaElTablero() {
        assertEquals("board/tablero-1", AppDestination.boardRoute("tablero-1"))
    }

    @Test
    fun upRoute_boardColumns_returnsBoardRoute() {
        assertEquals("board/tablero-1", AppDestination.BOARD_COLUMNS.upRoute("tablero-1"))
    }

    @Test
    fun upRoute_otherDestinations_returnNull() {
        val others = AppDestination.entries.filter { it != AppDestination.BOARD_COLUMNS }

        assertTrue(others.all { it.upRoute("tablero-1") == null })
    }

    @Test
    fun homeSections_mapeanASusDestinos() {
        assertEquals(
            listOf(
                AppDestination.MY_DAY,
                AppDestination.UPCOMING_7_DAYS,
                AppDestination.ALL_TASKS,
                AppDestination.CALENDAR,
            ),
            HomeSection.entries.map { it.destination },
        )
    }
}
