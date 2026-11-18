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
        val inbox = actions(RowContext.INBOX, inbox = true).sheet

        assertTrue(RowAction.MOVE_TO_LIST in inbox)
        assertEquals(RowAction.ASSIGN_TO_BOARD, inbox.last())
        assertEquals(inbox.indexOf(RowAction.MOVE_TO_LIST) + 1, inbox.indexOf(RowAction.ASSIGN_TO_BOARD))
    }

    @Test
    fun moverALista_soloEnLaPantallaTareas() {
        listOf(RowContext.MY_DAY, RowContext.PLANNED, RowContext.SUGGESTION).forEach { context ->
            assertFalse(context.name, RowAction.MOVE_TO_LIST in actions(context, marked = true, inbox = true).sheet)
        }
    }

    @Test
    fun miDia_derecha_completa() {
        assertEquals(RowAction.COMPLETE, actions(RowContext.MY_DAY, marked = true).swipeEnd)
    }

    @Test
    fun miDia_completada_derechaReabre() {
        val result = actions(RowContext.MY_DAY, completed = true, marked = true)

        assertEquals(RowAction.REOPEN, result.swipeEnd)
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
            listOf(RowAction.COMPLETE, RowAction.REMOVE_FROM_MY_DAY) + reschedule + RowAction.ASSIGN_TO_BOARD,
            inbox,
        )
        assertFalse(RowAction.ASSIGN_TO_BOARD in board)
    }

    @Test
    fun tareas_izquierda_alternaMiDia() {
        assertEquals(RowAction.ADD_TO_MY_DAY, actions(RowContext.INBOX, marked = false, inbox = true).swipeStart)
        assertEquals(RowAction.REMOVE_FROM_MY_DAY, actions(RowContext.INBOX, marked = true, inbox = true).swipeStart)
    }

    @Test
    fun tareas_bandeja_ofreceAsignar() {
        val sheet = actions(RowContext.INBOX, inbox = true).sheet

        assertEquals(RowAction.ASSIGN_TO_BOARD, sheet.last())
        assertEquals(listOf(RowAction.COMPLETE, RowAction.ADD_TO_MY_DAY), sheet.take(2))
    }

    @Test
    fun planificado_izquierda_esManana() {
        val result = actions(RowContext.PLANNED)

        assertEquals(RowAction.COMPLETE, result.swipeEnd)
        assertEquals(RowAction.RESCHEDULE_TOMORROW, result.swipeStart)
    }

    @Test
    fun planificado_noOfreceMiDia() {
        val sheet = actions(RowContext.PLANNED, marked = true, inbox = true).sheet

        assertEquals(listOf(RowAction.COMPLETE) + reschedule, sheet)
    }

    @Test
    fun sugerencia_derechaMantiene_izquierdaDescarta() {
        val result = actions(RowContext.SUGGESTION)

        assertEquals(RowAction.KEEP_TODAY, result.swipeEnd)
        assertEquals(RowAction.DISMISS, result.swipeStart)
    }

    @Test
    fun sugerencia_hojaOfreceMantenerMananaCompletarYDescartar() {
        assertEquals(
            listOf(RowAction.KEEP_TODAY, RowAction.RESCHEDULE_TOMORROW, RowAction.COMPLETE, RowAction.DISMISS),
            actions(RowContext.SUGGESTION).sheet,
        )
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
