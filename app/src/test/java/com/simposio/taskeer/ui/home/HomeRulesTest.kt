package com.simposio.taskeer.ui.home

import com.simposio.taskeer.ui.boards.BoardsUiState
import com.simposio.taskeer.ui.lists.TaskListsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRulesTest {

    @Test
    fun searchField_estaMarcadoWip() {
        assertFalse(HomeLayout.SEARCH_ENABLED)
    }

    @Test
    fun tileLabel_incluyeNombreYContador() {
        assertEquals("Compras, 2", tileLabel("Compras", 2))
    }

    @Test
    fun sinTableros_noHayEstadoVacio() {
        assertTrue(BoardsUiState().boards.isEmpty())
        val indicators = listOf(BoardsUiState::class.java, TaskListsUiState::class.java)
            .flatMap { it.declaredFields.map { field -> field.name.lowercase() } }

        assertTrue("el hub no expone indicadores de vacío: $indicators", indicators.none { "empty" in it })
    }
}
