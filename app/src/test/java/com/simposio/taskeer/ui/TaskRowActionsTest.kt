package com.simposio.taskeer.ui

import com.simposio.taskeer.ui.components.RowAction
import com.simposio.taskeer.ui.components.RowActions
import com.simposio.taskeer.ui.components.RowContext
import com.simposio.taskeer.ui.components.rowActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRowActionsTest {

    private fun actions(
        context: RowContext,
        completed: Boolean = false,
        marked: Boolean = false,
        inbox: Boolean = false,
        repeating: Boolean = false,
    ): RowActions = rowActions(context, completed, marked, inbox, repeating)

    private val reschedule = listOf(
        RowAction.RESCHEDULE_TOMORROW,
        RowAction.RESCHEDULE_NEXT_WEEK,
        RowAction.RESCHEDULE_SOMEDAY,
        RowAction.PICK_DATE,
    )

    @Test
    fun tareaDeLista_ofreceMoverALista() {
        val inbox = actions(RowContext.LIST, inbox = true).sheet

        assertTrue(RowAction.MOVE_TO_LIST in inbox)
        assertEquals(RowAction.DELETE, inbox.last())
        assertEquals(inbox.indexOf(RowAction.MOVE_TO_LIST) + 1, inbox.indexOf(RowAction.ASSIGN_TO_BOARD))
    }

    @Test
    fun moverALista_soloEnLaPantallaDeLista() {
        listOf(RowContext.MY_DAY, RowContext.PLANNED).forEach { context ->
            assertFalse(context.name, RowAction.MOVE_TO_LIST in actions(context, marked = true, inbox = true).sheet)
        }
    }

    @Test
    fun derecha_borraEnLosTresContextos() {
        RowContext.entries.forEach { context ->
            assertEquals(context.name, RowAction.DELETE, actions(context, marked = true, inbox = true).swipeEnd)
        }
    }

    @Test
    fun completar_yaNoEsGesto() {
        RowContext.entries.forEach { context ->
            listOf(false, true).forEach { completed ->
                val result = actions(context, completed = completed, marked = true, inbox = true)
                val gestures = listOfNotNull(result.swipeEnd, result.swipeStart)
                assertTrue(context.name, gestures.none { it == RowAction.COMPLETE || it == RowAction.REOPEN })
            }
        }
    }

    @Test
    fun hoja_terminaEnBorrarEnLosTresContextos() {
        RowContext.entries.forEach { context ->
            val sheet = actions(context, marked = true, inbox = true).sheet
            assertEquals(context.name, RowAction.DELETE, sheet.last())
            assertEquals(context.name, 1, sheet.count { it == RowAction.DELETE })
        }
    }

    @Test
    fun izquierda_noCambia() {
        assertEquals(RowAction.REMOVE_FROM_MY_DAY, actions(RowContext.MY_DAY, marked = true).swipeStart)
        assertEquals(RowAction.ADD_TO_MY_DAY, actions(RowContext.LIST, marked = false).swipeStart)
        assertEquals(RowAction.RESCHEDULE_TOMORROW, actions(RowContext.PLANNED).swipeStart)
    }

    @Test
    fun miDia_completada_ofreceReabrirEnLaHoja() {
        val result = actions(RowContext.MY_DAY, completed = true, marked = true)

        assertEquals(RowAction.REOPEN, result.sheet.first())
    }

    @Test
    fun miDia_izquierda_quitaDeMiDia() {
        assertEquals(RowAction.REMOVE_FROM_MY_DAY, actions(RowContext.MY_DAY, marked = true).swipeStart)
    }

    @Test
    fun miDia_asignadaSinMarca_noSePuedeQuitar() {
        val result = actions(RowContext.MY_DAY, marked = false)

        assertNull(result.swipeStart)
        assertFalse(RowAction.REMOVE_FROM_MY_DAY in result.sheet)
    }

    @Test
    fun miDia_hoja_incluyeReprogramarYAsignarSoloEnBandeja() {
        val inbox = actions(RowContext.MY_DAY, marked = true, inbox = true).sheet
        val board = actions(RowContext.MY_DAY, marked = true, inbox = false).sheet

        assertEquals(
            listOf(RowAction.COMPLETE, RowAction.REMOVE_FROM_MY_DAY) + reschedule +
                listOf(RowAction.ASSIGN_TO_BOARD, RowAction.DELETE),
            inbox,
        )
        assertFalse(RowAction.ASSIGN_TO_BOARD in board)
        assertEquals(RowAction.DELETE, board.last())
    }

    @Test
    fun lista_izquierda_alternaMiDia() {
        assertEquals(RowAction.ADD_TO_MY_DAY, actions(RowContext.LIST, marked = false, inbox = true).swipeStart)
        assertEquals(RowAction.REMOVE_FROM_MY_DAY, actions(RowContext.LIST, marked = true, inbox = true).swipeStart)
    }

    @Test
    fun lista_bandeja_ofreceAsignar() {
        val sheet = actions(RowContext.LIST, inbox = true).sheet

        assertEquals(listOf(RowAction.ASSIGN_TO_BOARD, RowAction.DELETE), sheet.takeLast(2))
        assertEquals(listOf(RowAction.COMPLETE, RowAction.ADD_TO_MY_DAY), sheet.take(2))
    }

    @Test
    fun planificado_izquierda_esManana() {
        val result = actions(RowContext.PLANNED)

        assertEquals(RowAction.DELETE, result.swipeEnd)
        assertEquals(RowAction.RESCHEDULE_TOMORROW, result.swipeStart)
    }

    @Test
    fun planificado_noOfreceMiDia() {
        val sheet = actions(RowContext.PLANNED, marked = true, inbox = true).sheet

        assertEquals(listOf(RowAction.COMPLETE) + reschedule + RowAction.DELETE, sheet)
    }

    @Test
    fun contextos_soloTieneMiDiaListaYPlanificado() {
        assertEquals(listOf("MY_DAY", "LIST", "PLANNED"), RowContext.entries.map { it.name })
    }

    @Test
    fun acciones_yaNoIncluyenLasDeSugerencia() {
        assertTrue(RowAction.entries.none { it.name == "KEEP_TODAY" || it.name == "DISMISS" })
    }

    @Test
    fun conRepeticion_ocultaReprogramar() {
        RowContext.entries.forEach { context ->
            val result = actions(context, marked = true, inbox = true, repeating = true)

            val offered = (result.sheet + listOfNotNull(result.swipeEnd, result.swipeStart)).toSet()
            assertTrue("$context ofrece reprogramar", offered.none { it in reschedule })
        }
        assertNull(actions(RowContext.PLANNED, repeating = true).swipeStart)
    }

    @Test
    fun deslizar_siempreEstaEnLaHoja() {
        RowContext.entries.forEach { context ->
            val result = actions(context, marked = true, inbox = true)

            listOfNotNull(result.swipeEnd, result.swipeStart).forEach { swipe ->
                assertTrue("$context: $swipe falta en la hoja", swipe in result.sheet)
            }
        }
    }
}
