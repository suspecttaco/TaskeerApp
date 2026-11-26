package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import com.simposio.taskeer.ui.workspaces.InvitationDialog
import com.simposio.taskeer.ui.workspaces.MyInvitationsUiState
import com.simposio.taskeer.ui.workspaces.WorkspaceInvitationsUiState
import java.lang.reflect.Modifier
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitationsSecretsRulesTest {

    private val directories = listOf("ui/workspaces", "data/remote/workspaces", "domain/workspace")
    private val sensitiveSinks = listOf(
        "Log.", "println(", "printStackTrace", "DataStore", "SharedPreferences", "edit()", "writeText", "Dao", "savedState",
    )

    @Test
    fun elTokenNoSePersisteNiSeRegistra() {
        val offenders = directories.flatMap { MainSources.kotlinFiles(it) }.flatMap { file ->
            file.readLines().filter { line ->
                val lower = line.lowercase()
                (lower.contains("token") || lower.contains("rawtext") || lower.contains("linkstate")) &&
                    sensitiveSinks.any { line.contains(it) }
            }.map { "${file.name}: ${it.trim()}" }
        }

        assertTrue("token junto a persistencia o logs: $offenders", offenders.isEmpty())
    }

    @Test
    fun elTokenNoPasaPorRememberSaveableNiSavedStateHandle() {
        val offenders = listOf(
            "ui/workspaces/MyInvitationsScreen.kt",
            "ui/workspaces/MyInvitationsViewModel.kt",
            "ui/workspaces/MyInvitationsUiState.kt",
        ).filter { path ->
            val text = runCatching { MainSources.kotlin(path) }.getOrDefault("")
            text.contains("rememberSaveable") || text.contains("SavedStateHandle")
        }

        assertTrue("estado guardado con el enlace: $offenders", offenders.isEmpty())
        assertTrue(!MainSources.kotlin("ui/workspaces/WorkspaceInvitationsSection.kt").contains("rememberSaveable"))
        assertTrue(!MainSources.kotlin("ui/workspaces/WorkspaceInvitationDialogs.kt").contains("rememberSaveable"))
    }

    @Test
    fun acceptWithToken_noGuardaElParametroEnNingunCampo() {
        val source = MainSources.kotlin("data/remote/workspaces/RemoteMyInvitationsRepository.kt")
        val body = source.substringAfter("override suspend fun acceptWithToken").substringBefore("private fun onSession")

        assertTrue(!Regex("""\b(this\.)?\w+\s*=\s*token\b""").containsMatchIn(body))
        assertTrue(body.contains("listOf(InvitationsApiContract.TOKEN_QUERY to token)"))
    }

    @Test
    fun elEstadoDeUiNoTieneCamposDeEnlaceNiToken() {
        val forbidden = listOf("token", "link", "raw", "input")
        val offenders = listOf(MyInvitationsUiState::class.java, WorkspaceInvitationsUiState::class.java, InvitationDialog.Invite::class.java)
            .flatMap { type -> type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) } }
            .filter { field -> forbidden.any { field.name.contains(it, ignoreCase = true) } }
            .map { it.name }

        assertTrue("campos secretos: $offenders", offenders.isEmpty())
    }

    @Test
    fun laRutaDeLaBandejaNoLlevaArgumentos() {
        val destination = MainSources.kotlin("ui/navigation/AppDestination.kt")

        assertTrue(destination.contains("WORKSPACE_INVITATIONS(\"workspaces/invitations\")"))
        assertTrue(!MainSources.kotlin("ui/navigation/AppNavHost.kt").contains("navArgument(\"token\""))
    }
}
