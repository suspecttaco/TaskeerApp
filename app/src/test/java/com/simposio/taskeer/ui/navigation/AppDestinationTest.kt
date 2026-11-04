package com.simposio.taskeer.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun boardColumnsNoEsSeccionPrincipal() {
        assertFalse("las columnas no son una sección de la barra", AppDestination.BOARD_COLUMNS in AppDestination.topLevel)
        assertEquals("board_columns/{boardId}", AppDestination.BOARD_COLUMNS.route)
    }

    @Test
    fun boardColumnsRouteLlevaElTablero() {
        assertEquals("board_columns/tablero-1", AppDestination.boardColumnsRoute("tablero-1"))
    }

    @Test
    fun boardRoute_llevaElTableroYNoEsSeccionPrincipal() {
        assertEquals("board/{boardId}", AppDestination.BOARD.route)
        assertEquals("board/tablero-1", AppDestination.boardRoute("tablero-1"))
        assertFalse(AppDestination.BOARD in AppDestination.topLevel)
        assertEquals(AppDestination.BOARD, AppDestination.fromRoute("board/{boardId}"))
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
    fun notifications_noEsTopLevel() {
        assertFalse(AppDestination.NOTIFICATIONS in AppDestination.topLevel)
        assertEquals("notifications", AppDestination.NOTIFICATIONS.route)
        assertEquals(AppDestination.NOTIFICATIONS, AppDestination.fromRoute("notifications"))
    }
}
