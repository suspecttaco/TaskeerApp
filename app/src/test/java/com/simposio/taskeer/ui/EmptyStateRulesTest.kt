package com.simposio.taskeer.ui

import com.simposio.taskeer.R
import com.simposio.taskeer.ui.tasks.EmptyAction
import com.simposio.taskeer.ui.tasks.EmptyScreen
import com.simposio.taskeer.ui.tasks.emptyStateFor
import org.junit.Assert.assertEquals
import org.junit.Test

class EmptyStateRulesTest {

    @Test
    fun miDia_vacioSinSugerencias_invitaAAnadir() {
        val spec = emptyStateFor(EmptyScreen.MY_DAY, suggestionCount = 0)

        assertEquals(EmptyAction.ADD_TASK, spec.action)
        assertEquals(R.string.empty_action_add_task, spec.actionRes)
        assertEquals(R.string.my_day_empty, spec.textRes)
    }

    @Test
    fun miDia_vacioConSugerencias_invitaAAbrirLaHoja() {
        val spec = emptyStateFor(EmptyScreen.MY_DAY, suggestionCount = 7)

        assertEquals(EmptyAction.SHOW_SUGGESTIONS, spec.action)
        assertEquals(R.string.empty_action_show_suggestions, spec.actionRes)
        assertEquals(R.string.my_day_empty_with_suggestions, spec.textRes)
        assertEquals(7, spec.actionCount)
    }

    @Test
    fun lista_vacia_ofreceAñadir() {
        val spec = emptyStateFor(EmptyScreen.LIST)

        assertEquals(EmptyAction.ADD_TASK, spec.action)
        assertEquals(R.string.list_empty, spec.textRes)
    }

    @Test
    fun proximos7Dias_vacio_ofreceAñadir() {
        val spec = emptyStateFor(EmptyScreen.UPCOMING)

        assertEquals(EmptyAction.ADD_TASK, spec.action)
        assertEquals(R.string.upcoming_empty, spec.textRes)
    }

    @Test
    fun todasLasTareas_vacio_ofreceAñadir() {
        val spec = emptyStateFor(EmptyScreen.ALL_TASKS)

        assertEquals(EmptyAction.ADD_TASK, spec.action)
        assertEquals(R.string.all_tasks_empty, spec.textRes)
    }

    @Test
    fun soloMiDiaOfreceVerSugerencias() {
        val others = EmptyScreen.entries.filter { it != EmptyScreen.MY_DAY }

        assertEquals(
            others.map { EmptyAction.ADD_TASK },
            others.map { emptyStateFor(it, suggestionCount = 9).action },
        )
    }
}
