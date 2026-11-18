package com.simposio.taskeer.ui

import com.simposio.taskeer.R
import com.simposio.taskeer.ui.tasks.EmptyAction
import com.simposio.taskeer.ui.tasks.EmptyScreen
import com.simposio.taskeer.ui.tasks.emptyStateFor
import org.junit.Assert.assertEquals
import org.junit.Test

class EmptyStateRulesTest {

    @Test
    fun miDia_vacioSinTareas_ofreceAñadir() {
        val spec = emptyStateFor(EmptyScreen.MY_DAY, hasTasks = false, filterActive = false)

        assertEquals(EmptyAction.ADD_TASK, spec.action)
        assertEquals(R.string.empty_action_add_task, spec.actionRes)
        assertEquals(R.string.my_day_empty, spec.textRes)
    }

    @Test
    fun miDia_vacioPorFiltro_ofreceVerTodas() {
        val spec = emptyStateFor(EmptyScreen.MY_DAY, hasTasks = true, filterActive = true)

        assertEquals(EmptyAction.SHOW_ALL, spec.action)
        assertEquals(R.string.my_day_filter_empty, spec.textRes)
    }

    @Test
    fun lista_vacia_ofreceAñadir() {
        val spec = emptyStateFor(EmptyScreen.LIST, hasTasks = false, filterActive = false)

        assertEquals(EmptyAction.ADD_TASK, spec.action)
        assertEquals(R.string.list_empty, spec.textRes)
    }

    @Test
    fun proximos7Dias_vacio_ofreceAñadir() {
        val spec = emptyStateFor(EmptyScreen.UPCOMING, hasTasks = false, filterActive = false)

        assertEquals(EmptyAction.ADD_TASK, spec.action)
        assertEquals(R.string.upcoming_empty, spec.textRes)
    }

    @Test
    fun todasLasTareas_vacio_ofreceAñadir() {
        val spec = emptyStateFor(EmptyScreen.ALL_TASKS, hasTasks = false, filterActive = false)

        assertEquals(EmptyAction.ADD_TASK, spec.action)
        assertEquals(R.string.all_tasks_empty, spec.textRes)
    }

    @Test
    fun soloMiDiaOfreceVerTodas() {
        val others = EmptyScreen.entries.filter { it != EmptyScreen.MY_DAY }

        assertEquals(
            others.map { EmptyAction.ADD_TASK },
            others.map { emptyStateFor(it, hasTasks = true, filterActive = true).action },
        )
    }
}
