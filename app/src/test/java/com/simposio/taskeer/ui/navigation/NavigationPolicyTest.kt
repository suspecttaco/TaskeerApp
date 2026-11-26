package com.simposio.taskeer.ui.navigation

import com.simposio.taskeer.ui.home.HomeSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPolicyTest {

    @Test
    fun homeNavSpec_recortaHastaHomeSinIncluirlo() {
        val spec = homeNavSpec()

        assertEquals("home", spec.popUpToRoute)
        assertFalse(spec.popUpToInclusive)
    }

    @Test
    fun homeNavSpec_unaSolaEntrada() {
        assertTrue(homeNavSpec().launchSingleTop)
    }

    @Test
    fun sectionNavSpec_desdeHome_recortaHastaHome() {
        listOf(HomeSection.UPCOMING_7_DAYS, HomeSection.ALL_TASKS, HomeSection.CALENDAR).forEach { section ->
            val spec = sectionNavSpec(section)

            assertEquals("home", spec.popUpToRoute)
            assertFalse(spec.popUpToInclusive)
            assertTrue(spec.launchSingleTop)
        }
    }

    @Test
    fun sectionNavSpec_miDia_recortaHastaMiDia() {
        val spec = sectionNavSpec(HomeSection.MY_DAY)

        assertEquals("my_day", spec.popUpToRoute)
        assertFalse(spec.popUpToInclusive)
    }

    @Test
    fun sectionNavSpec_sinSaveState() {
        HomeSection.entries.forEach { section ->
            val spec = sectionNavSpec(section)

            assertFalse(spec.saveState)
            assertFalse(spec.restoreState)
        }
    }

    @Test
    fun backStep_miDia_noHaceNada() {
        assertEquals(BackStep.None, backStep(AppDestination.MY_DAY, null, hasPrevious = true))
        assertEquals(BackStep.None, backStep(AppDestination.MY_DAY, null, hasPrevious = false))
    }

    @Test
    fun backStep_home_conAnterior_retrocede() {
        assertEquals(BackStep.Pop, backStep(AppDestination.HOME, null, hasPrevious = true))
    }

    @Test
    fun backStep_home_sinAnterior_navegaAMiDia() {
        assertEquals(BackStep.Navigate("my_day"), backStep(AppDestination.HOME, null, hasPrevious = false))
    }

    @Test
    fun backStep_seccion_sinAnterior_navegaAHome() {
        listOf(AppDestination.UPCOMING_7_DAYS, AppDestination.ALL_TASKS, AppDestination.CALENDAR).forEach {
            assertEquals(BackStep.Pop, backStep(it, null, hasPrevious = true))
            assertEquals(BackStep.Navigate("home"), backStep(it, null, hasPrevious = false))
        }
    }

    @Test
    fun backStep_lista_sinAnterior_navegaAHome() {
        assertEquals(BackStep.Pop, backStep(AppDestination.LIST, null, hasPrevious = true))
        assertEquals(BackStep.Navigate("home"), backStep(AppDestination.LIST, null, hasPrevious = false))
    }

    @Test
    fun backStep_tablero_sinAnterior_navegaAHome() {
        assertEquals(BackStep.Pop, backStep(AppDestination.BOARD, "b1", hasPrevious = true))
        assertEquals(BackStep.Navigate("home"), backStep(AppDestination.BOARD, "b1", hasPrevious = false))
    }

    @Test
    fun backStep_columnas_conservaSuComportamiento() {
        assertEquals(BackStep.Pop, backStep(AppDestination.BOARD_COLUMNS, "b1", hasPrevious = true))
        assertEquals(
            BackStep.Navigate("board/b1"),
            backStep(AppDestination.BOARD_COLUMNS, "b1", hasPrevious = false),
        )
    }

    @Test
    fun backStep_avisosYAjustes_sinAnterior_navegaAMiDia() {
        listOf(AppDestination.NOTIFICATIONS, AppDestination.SETTINGS).forEach {
            assertEquals(BackStep.Pop, backStep(it, null, hasPrevious = true))
            assertEquals(BackStep.Navigate("my_day"), backStep(it, null, hasPrevious = false))
        }
    }

    @Test
    fun backStep_desdeBusqueda_vuelveAHome() {
        assertEquals(BackStep.Navigate("home"), backStep(AppDestination.SEARCH, null, hasPrevious = false))
    }

    @Test
    fun backStep_desdeBusquedaConPila_hacePop() {
        assertEquals(BackStep.Pop, backStep(AppDestination.SEARCH, null, hasPrevious = true))
    }

    @Test
    fun shouldOpenViewer_conExtra_true() {
        assertTrue(shouldOpenViewer(hasExtra = true, isRestored = false, launchedFromHistory = false))
    }

    @Test
    fun shouldOpenViewer_sinExtra_false() {
        assertFalse(shouldOpenViewer(hasExtra = false, isRestored = false, launchedFromHistory = false))
    }

    @Test
    fun shouldOpenViewer_restaurada_false() {
        assertFalse(shouldOpenViewer(hasExtra = true, isRestored = true, launchedFromHistory = false))
    }

    @Test
    fun shouldOpenViewer_desdeHistorial_false() {
        assertFalse(shouldOpenViewer(hasExtra = true, isRestored = false, launchedFromHistory = true))
    }

    @Test
    fun register_caeALoginSinPantallaPrevia() {
        assertEquals(BackStep.Navigate("login"), backStep(AppDestination.REGISTER, null, hasPrevious = false))
        assertEquals(BackStep.Pop, backStep(AppDestination.REGISTER, null, hasPrevious = true))
    }

    @Test
    fun verifyEmail_caeASettings() {
        assertEquals(BackStep.Navigate("settings"), backStep(AppDestination.VERIFY_EMAIL, null, hasPrevious = false))
        assertEquals(BackStep.Pop, backStep(AppDestination.VERIFY_EMAIL, null, hasPrevious = true))
    }
}
