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
    fun miembros_noTocaRoom() {
        val files = listOf(
            "data/remote/workspaces/RemoteWorkspaceMembersRepository.kt",
            "data/remote/workspaces/WorkspaceMembersApiContract.kt",
            "domain/workspace/MemberRules.kt",
            "ui/workspaces/WorkspaceMembersViewModel.kt",
        ).map { it to MainSources.kotlin(it) }
        val localRepositories = listOf(
            "BoardRepository", "ColumnRepository", "TaskRepository", "TaskListRepository", "SubtaskRepository",
            "ReminderRepository", "PreferencesRepository", "MyDayRepository", "PlannedRepository",
        )

        val violations = files.flatMap { (path, source) ->
            source.lines().map { it.trim() }.filter { it.startsWith("import ") }.filter { line ->
                line.contains("data.local") || line.contains("androidx.room") ||
                    localRepositories.any { line.endsWith(".$it") || line.endsWith(".${it}Impl") }
            }.map { "$path: $it" }
        }

        assertTrue("los miembros no deben tocar datos locales: $violations", violations.isEmpty())
    }

    @Test
    fun invitaciones_noTocaRoom() {
        val files = listOf(
            "data/remote/workspaces/RemoteWorkspaceInvitationsRepository.kt",
            "data/remote/workspaces/RemoteMyInvitationsRepository.kt",
            "data/remote/workspaces/InvitationsApiContract.kt",
            "domain/workspace/InvitationRules.kt",
            "ui/workspaces/WorkspaceInvitationsViewModel.kt",
            "ui/workspaces/MyInvitationsViewModel.kt",
        ).map { it to MainSources.kotlin(it) }
        val localRepositories = listOf(
            "BoardRepository", "ColumnRepository", "TaskRepository", "TaskListRepository", "SubtaskRepository",
            "ReminderRepository", "PreferencesRepository", "MyDayRepository", "PlannedRepository",
        )

        val violations = files.flatMap { (path, source) ->
            source.lines().map { it.trim() }.filter { it.startsWith("import ") }.filter { line ->
                line.contains("data.local") || line.contains("androidx.room") ||
                    localRepositories.any { line.endsWith(".$it") || line.endsWith(".${it}Impl") }
            }.map { "$path: $it" }
        }

        assertTrue("las invitaciones no deben tocar datos locales: $violations", violations.isEmpty())
    }

    @Test
    fun elContenedorNoCruzaWorkspacesConDatosLocales() {
        val viewModel = MainSources.kotlinFiles("ui/workspaces").joinToString("\n") { it.readText() }

        assertTrue(!viewModel.contains("BoardRepository") && !viewModel.contains("TaskRepository") && !viewModel.contains("Dao"))
    }

    @Test
    fun tablerosDelServidor_noTocaRoom() {
        val files = MainSources.kotlinFiles("data/remote/boards").map { it.name to it.readText() } + listOf(
            "ServerBoardsViewModel.kt" to MainSources.kotlin("ui/workspaces/ServerBoardsViewModel.kt"),
            "BoardAccessViewModel.kt" to MainSources.kotlin("ui/workspaces/BoardAccessViewModel.kt"),
            "BoardRoleRules.kt" to MainSources.kotlin("domain/workspace/BoardRoleRules.kt"),
        )
        val localRepositories = listOf(
            "BoardRepository", "ColumnRepository", "TaskRepository", "TaskListRepository", "SubtaskRepository",
            "ReminderRepository", "PreferencesRepository", "MyDayRepository", "PlannedRepository",
        )

        val violations = files.flatMap { (name, source) ->
            source.lines().map { it.trim() }.filter { it.startsWith("import ") }.filter { line ->
                line.contains("data.local") || line.contains("androidx.room") ||
                    localRepositories.any { line.endsWith(".$it") || line.endsWith(".${it}Impl") }
            }.map { "$name: $it" }
        }

        assertTrue("los tableros del servidor no deben tocar datos locales: $violations", violations.isEmpty())
        assertTrue(files.size >= 5)
    }
}
