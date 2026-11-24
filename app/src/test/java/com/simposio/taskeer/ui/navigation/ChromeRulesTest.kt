package com.simposio.taskeer.ui.navigation

import com.simposio.taskeer.ui.quickadd.QuickAddContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeRulesTest {

    private val sections = listOf(
        AppDestination.MY_DAY,
        AppDestination.UPCOMING_7_DAYS,
        AppDestination.ALL_TASKS,
        AppDestination.CALENDAR,
        AppDestination.LIST,
    )

    @Test
    fun secciones_muestranHomeYPildora() {
        sections.forEach {
            val chrome = chromeFor(it)
            assertEquals(TopBarKind.SECTION, chrome.topBar)
            assertTrue(chrome.showHomeButton && chrome.showPill)
        }
    }

    @Test
    fun sugerencias_soloEnMiDia() {
        assertEquals(
            listOf(AppDestination.MY_DAY),
            AppDestination.entries.filter { chromeFor(it).showSuggestions },
        )
    }

    @Test
    fun sugerencias_noCambiaElResto() {
        assertEquals(
            Chrome(TopBarKind.SECTION, showHomeButton = true, showPill = true, showSuggestions = true),
            chromeFor(AppDestination.MY_DAY),
        )
        (sections - AppDestination.MY_DAY - AppDestination.CALENDAR).forEach {
            assertEquals(Chrome(TopBarKind.SECTION, showHomeButton = true, showPill = true), chromeFor(it))
        }
    }

    @Test
    fun calendario_pildoraEsDeEvento() {
        assertEquals(
            Chrome(TopBarKind.SECTION, showHomeButton = true, showPill = true, pill = PillKind.EVENT),
            chromeFor(AppDestination.CALENDAR),
        )
    }

    @Test
    fun soloElCalendarioTienePildoraDeEvento() {
        assertEquals(
            listOf(AppDestination.CALENDAR),
            AppDestination.entries.filter { chromeFor(it).pill == PillKind.EVENT },
        )
    }

    @Test
    fun hub_noMuestraHomeNiPildora() {
        assertEquals(Chrome(TopBarKind.HUB, showHomeButton = false, showPill = false), chromeFor(AppDestination.HOME))
    }

    @Test
    fun tablero_muestraHomeSinPildora() {
        assertEquals(
            Chrome(TopBarKind.DETAIL_WITH_MENU, showHomeButton = true, showPill = false),
            chromeFor(AppDestination.BOARD),
        )
    }

    @Test
    fun utilidades_noMuestranCromoFlotante() {
        listOf(AppDestination.BOARD_COLUMNS, AppDestination.NOTIFICATIONS, AppDestination.SETTINGS).forEach {
            assertEquals(Chrome(TopBarKind.DETAIL, showHomeButton = false, showPill = false), chromeFor(it))
        }
    }

    @Test
    fun chromeFor_cubreTodosLosDestinos() {
        val chromes = AppDestination.entries.map { chromeFor(it) }

        assertEquals(AppDestination.entries.size, chromes.size)
        assertTrue(chromes.all { !it.showPill || it.showHomeButton })
    }

    @Test
    fun topBar_porDestino() {
        assertEquals(TopBarKind.SECTION, chromeFor(AppDestination.MY_DAY).topBar)
        assertEquals(TopBarKind.HUB, chromeFor(AppDestination.HOME).topBar)
        assertEquals(TopBarKind.DETAIL, chromeFor(AppDestination.SETTINGS).topBar)
        assertEquals(TopBarKind.DETAIL_WITH_MENU, chromeFor(AppDestination.BOARD).topBar)
    }

    @Test
    fun menu_miDia() {
        assertEquals(
            listOf(MenuItem.CLEAR_COMPLETED, MenuItem.SETTINGS),
            overflowMenuFor(AppDestination.MY_DAY).items,
        )
    }

    @Test
    fun menu_proximos7YTodasLasTareas_incluyenFiltrarYOrdenar() {
        val expected = listOf(MenuItem.CLEAR_COMPLETED, MenuItem.FILTER, MenuItem.SORT, MenuItem.SETTINGS)

        assertEquals(expected, overflowMenuFor(AppDestination.UPCOMING_7_DAYS).items)
        assertEquals(expected, overflowMenuFor(AppDestination.ALL_TASKS).items)
    }

    @Test
    fun menu_calendario() {
        assertEquals(
            listOf(MenuItem.CLEAR_COMPLETED, MenuItem.MY_CALENDARS, MenuItem.SETTINGS),
            overflowMenuFor(AppDestination.CALENDAR).items,
        )
    }

    @Test
    fun menu_lista() {
        assertEquals(
            listOf(MenuItem.CLEAR_COMPLETED, MenuItem.RENAME_LIST, MenuItem.DELETE_LIST, MenuItem.SETTINGS),
            overflowMenuFor(AppDestination.LIST).items,
        )
    }

    @Test
    fun menu_tablero() {
        assertEquals(
            listOf(MenuItem.RENAME_BOARD, MenuItem.DELETE_BOARD),
            overflowMenuFor(AppDestination.BOARD).items,
        )
    }

    @Test
    fun menu_miDiaNoOfreceFiltrarNiOrdenar() {
        val items = overflowMenuFor(AppDestination.MY_DAY).items

        assertFalse(MenuItem.FILTER in items)
        assertFalse(MenuItem.SORT in items)
    }

    @Test
    fun menu_botonesSuperioresSonLosMismos() {
        val expected = listOf(MenuAction.SYNC, MenuAction.PRINT, MenuAction.SHARE)

        sections.forEach { assertEquals(expected, overflowMenuFor(it).actions) }
    }

    @Test
    fun menu_ningunoOfreceElementosDescartados() {
        val names = MenuItem.entries.map { it.name }

        listOf("IMPORT", "MOMENT", "TAGS", "AI", "PREMIUM", "SHOPPING").forEach {
            assertFalse("$it no debe existir", it in names)
        }
    }

    @Test
    fun menu_ajustesEnTodasLasSecciones() {
        sections.forEach { assertTrue(MenuItem.SETTINGS in overflowMenuFor(it).items) }
    }

    @Test
    fun menu_destinosSinMenu_vacio() {
        listOf(
            AppDestination.HOME,
            AppDestination.BOARD_COLUMNS,
            AppDestination.NOTIFICATIONS,
            AppDestination.SETTINGS,
        ).forEach { assertEquals(OverflowMenu(emptyList(), emptyList()), overflowMenuFor(it)) }
    }

    @Test
    fun quickAddContextFor_miDia() {
        assertEquals(QuickAddContext.MyDay, quickAddContextFor(AppDestination.MY_DAY, null))
    }

    @Test
    fun quickAddContextFor_lista() {
        assertEquals(QuickAddContext.InList("compras"), quickAddContextFor(AppDestination.LIST, "compras"))
        assertEquals(QuickAddContext.Default, quickAddContextFor(AppDestination.LIST, null))
    }

    @Test
    fun quickAddContextFor_vistasPorFecha() {
        assertEquals(QuickAddContext.Default, quickAddContextFor(AppDestination.UPCOMING_7_DAYS, null))
        assertEquals(QuickAddContext.Default, quickAddContextFor(AppDestination.ALL_TASKS, null))
    }

    @Test
    fun quickAddContextFor_calendario() {
        assertEquals(QuickAddContext.Default, quickAddContextFor(AppDestination.CALENDAR, null))
    }

    @Test
    fun canDeleteList_soloConMasDeUnaLista() {
        assertFalse(canDeleteList(1))
        assertTrue(canDeleteList(2))
    }

    @Test
    fun withListCount_conUnaLista_ocultaEliminarLista() {
        val menu = overflowMenuFor(AppDestination.LIST)

        assertFalse(MenuItem.DELETE_LIST in menu.withListCount(1).items)
        assertTrue(MenuItem.DELETE_LIST in menu.withListCount(2).items)
        assertTrue(MenuItem.RENAME_LIST in menu.withListCount(1).items)
    }
}
