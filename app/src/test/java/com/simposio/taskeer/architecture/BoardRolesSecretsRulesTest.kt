package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardRolesSecretsRulesTest {

    private val files = listOf(
        "data/remote/boards/BoardRolesApiContract.kt",
        "data/remote/boards/RemoteServerBoardsRepository.kt",
        "data/remote/boards/RemoteBoardAccessRepository.kt",
        "data/repository/RoleChangeSignals.kt",
        "domain/workspace/ServerBoard.kt",
        "domain/workspace/BoardMemberOverride.kt",
        "domain/workspace/BoardRoleRules.kt",
        "domain/notification/LiveRoleRules.kt",
        "ui/workspaces/ServerBoardsViewModel.kt",
        "ui/workspaces/ServerBoardsUiState.kt",
        "ui/workspaces/ServerBoardListScreen.kt",
        "ui/workspaces/BoardAccessViewModel.kt",
        "ui/workspaces/BoardAccessUiState.kt",
        "ui/workspaces/BoardAccessScreenRules.kt",
        "ui/workspaces/BoardAccessScreen.kt",
        "ui/workspaces/BoardAccessDialogs.kt",
        "ui/workspaces/BoardRoleErrorRules.kt",
    )

    @Test
    fun sinLogsDeContenido() {
        val logging = Regex("""\bLog\.[a-z]\(|\bprintln\(|\bprint\(|Timber\.|printStackTrace""")

        val offenders = files.filter { logging.containsMatchIn(MainSources.kotlin(it)) }

        assertTrue("correo, nombre, userId y nombre de tablero son datos personales; sin logs: $offenders", offenders.isEmpty())
    }

    @Test
    fun elRepositorioNoEscribeDatosEnMensajesDeError() {
        listOf(
            "data/remote/boards/RemoteServerBoardsRepository.kt",
            "data/remote/boards/RemoteBoardAccessRepository.kt",
        ).forEach {
            assertTrue(it, !Regex("""DomainError\.\w+\([^)]*\$""").containsMatchIn(MainSources.kotlin(it)))
        }
    }

    @Test
    fun losModelosDeUiRedactanElToString() {
        val models = mapOf(
            "domain/workspace/ServerBoard.kt" to listOf("\$name"),
            "domain/workspace/BoardMemberOverride.kt" to listOf("\$userId", "\$boardName", "\${userId}", "\${boardName}"),
            "ui/workspaces/ServerBoardsUiState.kt" to listOf("\$name"),
            "ui/workspaces/BoardAccessUiState.kt" to listOf("\$name", "\$email", "\$userId", "\$title", "\$boardName", "\${title}"),
        )

        models.forEach { (path, forbidden) ->
            val toStrings = MainSources.kotlin(path).lines().filter { it.contains("override fun toString()") }
            assertTrue(path, toStrings.isNotEmpty())
            toStrings.forEach { line -> assertTrue("$path: $line", forbidden.none { line.contains(it) }) }
        }
    }

    @Test
    fun laSenalNoLlevaContenido() {
        val source = MainSources.kotlin("data/repository/RoleChangeSignals.kt")

        assertTrue(source.contains("val signals: Flow<Unit>"))
        assertTrue(source.contains("override val signals: Flow<Unit>"))
        assertTrue(!source.contains("ServerNotification"))
    }
}
