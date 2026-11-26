package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceLocalIsolationTest {

    @Test
    fun workspaces_noTocaRoom() {
        val files = MainSources.kotlinFiles("data/remote/workspaces") + MainSources.kotlinFiles("domain/workspace")
        val localRepositories = listOf(
            "BoardRepository", "ColumnRepository", "TaskRepository", "TaskListRepository", "SubtaskRepository",
            "ReminderRepository", "PreferencesRepository", "MyDayRepository", "PlannedRepository",
        )

        val violations = files.flatMap { file ->
            val imports = file.readLines().map { it.trim() }.filter { it.startsWith("import ") }
            imports.filter { line ->
                line.contains("data.local") || line.contains("androidx.room") ||
                    localRepositories.any { line.endsWith(".$it") || line.endsWith(".${it}Impl") }
            }.map { "${file.name}: $it" }
        }

        assertTrue("workspaces no debe tocar datos locales: $violations", violations.isEmpty())
        assertTrue(files.isNotEmpty())
    }

    @Test
    fun elContenedorNoCruzaWorkspacesConDatosLocales() {
        val viewModel = MainSources.kotlinFiles("ui/workspaces").joinToString("\n") { it.readText() }

        assertTrue(!viewModel.contains("BoardRepository") && !viewModel.contains("TaskRepository") && !viewModel.contains("Dao"))
    }
}
