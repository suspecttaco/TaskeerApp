package com.simposio.taskeer.ui

import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.ui.components.RowContext
import com.simposio.taskeer.ui.components.TaskRowUi
import com.simposio.taskeer.ui.tasks.firstPeekableId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirstPeekableIdTest {

    private fun row(id: String, marked: Boolean = false, repeating: Boolean = false) = TaskRowUi(
        id = id,
        title = id,
        isCompleted = false,
        priority = TaskPriority.MEDIUM,
        due = null,
        boardName = null,
        showBoard = false,
        isOverdue = false,
        isMarked = marked,
        isInbox = false,
        isRepeating = repeating,
        showMyDayStar = false,
    )

    @Test
    fun miDia_saltaLasFilasSinAccionALaIzquierda() {
        val rows = listOf(row("a", marked = false), row("b", marked = true))

        assertEquals("b", firstPeekableId(rows, RowContext.MY_DAY))
    }

    @Test
    fun planificado_saltaLasRepetidas() {
        val rows = listOf(row("a", repeating = true), row("b"))

        assertEquals("b", firstPeekableId(rows, RowContext.PLANNED))
    }

    @Test
    fun lista_laPrimeraFilaSiempreTieneAccion() {
        assertEquals("a", firstPeekableId(listOf(row("a"), row("b")), RowContext.LIST))
    }

    @Test
    fun sinFilasConAccion_esNulo() {
        assertNull(firstPeekableId(listOf(row("a")), RowContext.MY_DAY))
        assertNull(firstPeekableId(emptyList(), RowContext.LIST))
    }
}
