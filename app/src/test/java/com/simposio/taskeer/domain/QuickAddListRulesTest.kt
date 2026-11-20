package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.list.effectiveListId
import com.simposio.taskeer.testing.listSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickAddListRulesTest {

    private val lists = listOf(
        listSummary("tareas", "Tareas", position = 0),
        listSummary("compras", "Compras", position = 1),
        listSummary("trabajo", "Trabajo", position = 2),
    )

    @Test
    fun effectiveListId_elegidaValida() {
        assertEquals("trabajo", effectiveListId(lists, chosen = "trabajo", contextListId = "compras"))
    }

    @Test
    fun effectiveListId_elegidaInexistente_caeAlContexto() {
        assertEquals("compras", effectiveListId(lists, chosen = "borrada", contextListId = "compras"))
    }

    @Test
    fun effectiveListId_contextoValido() {
        assertEquals("compras", effectiveListId(lists, chosen = null, contextListId = "compras"))
    }

    @Test
    fun effectiveListId_contextoInexistente_caeALaPorDefecto() {
        assertEquals("tareas", effectiveListId(lists, chosen = null, contextListId = "borrada"))
    }

    @Test
    fun effectiveListId_sinElegirNiContexto_esLaPorDefecto() {
        assertEquals("tareas", effectiveListId(lists, chosen = null, contextListId = null))
    }

    @Test
    fun effectiveListId_sinListas_esNulo() {
        assertNull(effectiveListId(emptyList(), chosen = "tareas", contextListId = "compras"))
    }

    @Test
    fun effectiveListId_laPorDefectoEsLaDeMenorPosicionAunqueSeRenombre() {
        val renamed = listOf(
            listSummary("compras", "Zeta", position = 1),
            listSummary("tareas", "Inbox", position = 0),
        )

        assertEquals("tareas", effectiveListId(renamed, chosen = null, contextListId = null))
    }
}
