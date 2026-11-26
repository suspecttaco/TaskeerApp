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
                "SEARCH" to "search",
                "NOTIFICATIONS" to "notifications",
                "SETTINGS" to "settings",
                "LOGIN" to "login",
                "REGISTER" to "register",
                "VERIFY_EMAIL" to "verify-email",
                "RESET_PASSWORD" to "reset-password",
                "CHANGE_PASSWORD" to "change-password",
                "REGENERATE_CODES" to "regenerate-codes",
            ),
            AppDestination.entries.associate { it.name to it.route },
        )
    }

    @Test
    fun search_tieneRutaYNoEsDeSeccion() {
        assertEquals("search", AppDestination.SEARCH.route)
        assertEquals(AppDestination.SEARCH, AppDestination.fromRoute("search"))
        assertFalse(AppDestination.SEARCH.route.contains("{"))
        assertNull(AppDestination.SEARCH.upRoute("b1"))
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

    @Test
    fun noHayRutasDeErrores() {
        assertTrue(AppDestination.entries.none { it.name.startsWith("ERROR") || it.route.startsWith("error") })
    }

    @Test
    fun login_tieneRutaLogin() {
        assertEquals("login", AppDestination.LOGIN.route)
        assertEquals(AppDestination.LOGIN, AppDestination.fromRoute("login"))
    }

    @Test
    fun register_tieneRutaRegister() {
        assertEquals("register", AppDestination.REGISTER.route)
        assertEquals(AppDestination.REGISTER, AppDestination.fromRoute("register"))
    }

    @Test
    fun verifyEmail_tieneRutaVerifyEmail() {
        assertEquals("verify-email", AppDestination.VERIFY_EMAIL.route)
        assertEquals(AppDestination.VERIFY_EMAIL, AppDestination.fromRoute("verify-email"))
    }

    @Test
    fun resetPassword_tieneRutaResetPassword() {
        assertEquals("reset-password", AppDestination.RESET_PASSWORD.route)
        assertEquals(AppDestination.RESET_PASSWORD, AppDestination.fromRoute("reset-password"))
    }

    @Test
    fun changePassword_tieneRutaChangePassword() {
        assertEquals("change-password", AppDestination.CHANGE_PASSWORD.route)
        assertEquals(AppDestination.CHANGE_PASSWORD, AppDestination.fromRoute("change-password"))
    }

    @Test
    fun regenerateCodes_tieneRutaRegenerateCodes() {
        assertEquals("regenerate-codes", AppDestination.REGENERATE_CODES.route)
        assertEquals(AppDestination.REGENERATE_CODES, AppDestination.fromRoute("regenerate-codes"))
    }

    @Test
    fun lasTresRutasDeSeguridad_noLlevanArgumentos() {
        listOf(AppDestination.RESET_PASSWORD, AppDestination.CHANGE_PASSWORD, AppDestination.REGENERATE_CODES)
            .forEach { assertFalse(it.route.contains("{")) }
    }
}
