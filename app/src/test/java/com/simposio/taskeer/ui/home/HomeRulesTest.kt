package com.simposio.taskeer.ui.home

import com.simposio.taskeer.ui.boards.BoardsUiState
import com.simposio.taskeer.ui.lists.TaskListsUiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRulesTest {

    @Test
    fun searchEntry_ya_noEstaMarcadoWip() {
        val home = sourceOf("ui/home/HomeSection.kt") + sourceOf("ui/home/HomeScreen.kt")
        val strings = File(sourceRoot(), "../../../../res/values/strings.xml").readText()

        assertFalse(home.contains("SEARCH_ENABLED"))
        assertFalse(strings.contains("home_search_wip"))
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

    @Test
    fun mosaicoDeWorkspaces_soloConSesion() {
        val home = sourceOf("ui/home/HomeScreen.kt")

        assertTrue(home.contains("if (showWorkspaces) {"))
        assertTrue(home.contains("showWorkspaces: Boolean = false"))
        assertTrue(home.contains("showWorkspaces = WorkspacesScreenRules.showEntry(sessionState)"))
    }

    private fun sourceOf(path: String): String = File(sourceRoot(), path).readText()

    private fun sourceRoot(): File {
        val relative = "app/src/main/java/com/simposio/taskeer"
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(start) { it.parentFile }.map { File(it, relative) }.first { it.isDirectory }
    }
}
