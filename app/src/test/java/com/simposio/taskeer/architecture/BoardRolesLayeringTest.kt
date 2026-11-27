package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardRolesLayeringTest {

    private val contracts = listOf(
        "data/repository/ServerBoardsRepository.kt",
        "data/repository/BoardAccessRepository.kt",
        "data/repository/RoleChangeSignals.kt",
    )

    private val domainFiles = listOf(
        "domain/workspace/ServerBoard.kt",
        "domain/workspace/BoardMemberOverride.kt",
        "domain/workspace/BoardRoleRules.kt",
        "domain/notification/LiveRoleRules.kt",
    )

    private val newFiles: List<String> = contracts + domainFiles +
        MainSources.kotlinFiles("data/remote/boards").map { "data/remote/boards/${it.name}" } +
        listOf(
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
    fun contratosNoExponenTiposRemotos() {
        contracts.forEach {
            val source = MainSources.kotlin(it)
            assertTrue(it, listOf("data.remote", "org.json", "okhttp3", "android.").none { forbidden -> source.contains(forbidden) })
        }
    }

    @Test
    fun sinRoomNiDataStore() {
        val violations = newFiles.flatMap { path ->
            listOf("androidx.room", "androidx.datastore", "data.local", "SharedPreferences", "java.io.File")
                .filter { MainSources.kotlin(path).contains(it) }
                .map { "$path referencia $it" }
        }

        assertTrue("tableros del servidor y overrides viven solo en memoria: $violations", violations.isEmpty())
        assertTrue(newFiles.size >= 15)
    }

    @Test
    fun elEsquemaDeRoomConservaElJsonDeLaVersion15() {
        val schemas = MainSources.kotlinRoot.resolve("../../../../../../schemas").canonicalFile.walkTopDown()
            .filter { it.isFile && it.extension == "json" }.map(File::getName).toList()

        assertTrue(schemas.contains("15.json"))
    }

    @Test
    fun noImportanDataLocalNiTablerosLocales() {
        val localRepositories = listOf(
            "BoardRepository", "ColumnRepository", "TaskRepository", "TaskListRepository", "SubtaskRepository",
            "ReminderRepository", "PreferencesRepository", "MyDayRepository", "PlannedRepository",
        )

        val violations = newFiles.flatMap { path ->
            MainSources.kotlin(path).lines().map { it.trim() }.filter { it.startsWith("import ") }.filter { line ->
                line.contains("data.local") || line.contains("androidx.room") ||
                    localRepositories.any { line.endsWith(".$it") || line.endsWith(".${it}Impl") }
            }.map { "$path: $it" }
        }

        assertTrue("no deben tocar datos locales: $violations", violations.isEmpty())
    }

    @Test
    fun noHayOtroCanalSse() {
        val forbidden = listOf("StreamTransport", "stream-ticket", "notifications/stream", "okhttp3", "RemoteNotificationLiveChannel")

        val violations = newFiles.flatMap { path ->
            forbidden.filter { MainSources.kotlin(path).contains(it) }.map { "$path referencia $it" }
        }

        assertTrue("la señal sale del canal existente, sin otra conexión: $violations", violations.isEmpty())
    }

    @Test
    fun laSenalSoloDependeDeContratos() {
        val source = MainSources.kotlin("data/repository/RoleChangeSignals.kt")
        val imports = source.lines().filter { it.startsWith("import ") }

        assertTrue(imports.none { it.contains("data.remote") || it.contains("com.simposio.taskeer.ui") })
        assertEquals(1, Regex("class LiveRoleChangeSignals\\(").findAll(source).count())
    }

    @Test
    fun laInterfazDeUsuarioNoConoceLaCapaRemotaNiLocal() {
        val violations = newFiles.filter { it.startsWith("ui/") }.flatMap { path ->
            listOf("data.remote", "data.local").filter { MainSources.kotlin(path).contains(it) }.map { "$path referencia $it" }
        }

        assertTrue(violations.toString(), violations.isEmpty())
    }

    @Test
    fun elCanalYElTransporteNoCambian() {
        listOf(
            "data/remote/notifications/RemoteNotificationLiveChannel.kt",
            "data/remote/StreamTransport.kt",
            "data/remote/transport/OkHttpStreamTransport.kt",
        ).forEach {
            val source = MainSources.kotlin(it)
            assertTrue(it, !source.contains("liveArrivals") && !source.contains("RoleChange"))
        }
    }
}
