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
    fun tareas_vacia_ofreceAñadir() {
        val spec = emptyStateFor(EmptyScreen.INBOX, hasTasks = false, filterActive = false)

        assertEquals(EmptyAction.ADD_TASK, spec.action)
        assertEquals(R.string.inbox_empty, spec.textRes)
    }

    @Test
    fun planificado_vacio_ofreceIrATareas() {
        val spec = emptyStateFor(EmptyScreen.PLANNED, hasTasks = false, filterActive = false)

        assertEquals(EmptyAction.GO_TO_INBOX, spec.action)
        assertEquals(R.string.empty_action_go_inbox, spec.actionRes)
    }
}
