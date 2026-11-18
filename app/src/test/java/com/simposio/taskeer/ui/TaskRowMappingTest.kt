package com.simposio.taskeer.ui

import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.ui.inbox.InboxTaskUi
import com.simposio.taskeer.ui.myday.MyDaySuggestionUi
import com.simposio.taskeer.ui.myday.MyDayTaskUi
import com.simposio.taskeer.ui.planned.PlannedTaskUi
import com.simposio.taskeer.ui.tasks.toRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRowMappingTest {

    private fun myDay(isOverdue: Boolean = false, isCompleted: Boolean = false) = MyDayTaskUi(
        "t1", "Informe", TaskPriority.HIGH, "Proyecto", "1 oct 2026, 9:00",
        isOverdue = isOverdue, isMarked = true, isAssigned = false, isCompleted = isCompleted, isRepeating = true,
    )

    @Test
    fun miDia_muestraFechaYTablero() {
        val row = myDay().toRow()

        assertEquals("1 oct 2026, 9:00", row.due)
        assertEquals("Proyecto", row.boardName)
        assertTrue(row.showBoard)
        assertFalse(row.showMyDayStar)
        assertTrue(row.isRepeating)
    }

    @Test
    fun tareas_noMuestraTablero() {
        val row = InboxTaskUi("t2", "Pan", TaskPriority.LOW, "1 oct 2026, 9:00", isOverdue = false, isMarked = true).toRow()

        assertFalse(row.showBoard)
        assertNull(row.boardName)
        assertTrue(row.isInbox)
        assertEquals("1 oct 2026, 9:00", row.due)
        assertTrue(row.showMyDayStar)
    }

    @Test
    fun tareas_sinMarca_noMuestraEstrella() {
        val row = InboxTaskUi("t2", "Pan", TaskPriority.LOW, null, isOverdue = false, isMarked = false).toRow()

        assertFalse(row.showMyDayStar)
    }

    @Test
    fun contexto_listaOTablero() {
        val inList = MyDayTaskUi(
            "t1", "Leche", TaskPriority.LOW, null, "x", isOverdue = false, isMarked = false, isAssigned = false,
            listName = "Compras",
        ).toRow()
        val inBoard = myDay().toRow()
        val planned = PlannedTaskUi("t3", "Leche", TaskPriority.LOW, null, "10:30", isOverdue = false, listName = "Compras").toRow()
        val suggestion = MyDaySuggestionUi("s1", "Leche", null, listName = "Compras").toRow()

        assertEquals("Compras", inList.listName)
        assertNull(inList.boardName)
        assertTrue(inList.showBoard)
        assertEquals("Proyecto", inBoard.boardName)
        assertNull(inBoard.listName)
        assertEquals("Compras", planned.listName)
        assertEquals("Compras", suggestion.listName)
    }

    @Test
    fun planificado_muestraSoloLaHora() {
        val row = PlannedTaskUi("t3", "Banco", TaskPriority.URGENT, "Casa", "10:30", isOverdue = false).toRow()

        assertEquals("10:30", row.due)
        assertEquals("Casa", row.boardName)
        assertTrue(row.showBoard)
        assertFalse(row.isCompleted)
    }

    @Test
    fun planificado_sinHora_noTieneFecha() {
        assertNull(PlannedTaskUi("t3", "Banco", TaskPriority.URGENT, null, "", isOverdue = false).toRow().due)
    }

    @Test
    fun vencida_marcaIsOverdue() {
        assertTrue(myDay(isOverdue = true).toRow().isOverdue)
        assertTrue(PlannedTaskUi("t3", "Banco", TaskPriority.LOW, null, "9:00", isOverdue = true).toRow().isOverdue)
        assertTrue(InboxTaskUi("t2", "Pan", TaskPriority.LOW, "x", isOverdue = true, isMarked = false).toRow().isOverdue)
    }

    @Test
    fun completada_conservaSuEstado() {
        assertTrue(myDay(isCompleted = true).toRow().isCompleted)
        assertTrue(InboxTaskUi("t2", "Pan", TaskPriority.LOW, null, false, false, isCompleted = true).toRow().isCompleted)
    }

    @Test
    fun sugerencia_noEstaMarcadaYConservaRepeticionYPrioridad() {
        val row = MyDaySuggestionUi("s1", "Revisar", null, TaskPriority.URGENT, "hoy", isOverdue = true, isRepeating = true).toRow()

        assertFalse(row.isMarked)
        assertTrue(row.isInbox)
        assertTrue(row.isRepeating)
        assertEquals(TaskPriority.URGENT, row.priority)
    }
}
