package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceLayeringTest {

    private val workspaceFiles = MainSources.kotlinFiles("data/remote/workspaces") +
        MainSources.kotlinFiles("domain/workspace") +
        MainSources.kotlinFiles("data/repository").filter { it.name == "WorkspaceRepository.kt" }

    @Test
    fun noUsaRoomNiDataStore() {
        val violations = workspaceFiles.flatMap { file ->
            listOf("androidx.room", "androidx.datastore", "data.local", "SharedPreferences")
                .filter { file.readText().contains(it) }
                .map { "${file.name} referencia $it" }
        }

        assertTrue("los workspaces del servidor viven solo en memoria: $violations", violations.isEmpty())
        assertTrue(workspaceFiles.size >= 6)
    }

    @Test
    fun miembros_noUsanRoomNiDataStore() {
        val files = listOf(
            "data/remote/workspaces/RemoteWorkspaceMembersRepository.kt",
            "data/remote/workspaces/WorkspaceMembersApiContract.kt",
            "data/repository/WorkspaceMembersRepository.kt",
            "domain/workspace/WorkspaceMember.kt",
            "domain/workspace/MemberRules.kt",
        )

        val violations = files.flatMap { path ->
            listOf("androidx.room", "androidx.datastore", "data.local", "SharedPreferences", "java.io.File")
                .filter { MainSources.kotlin(path).contains(it) }
                .map { "$path referencia $it" }
        }

        assertTrue("los miembros viven solo en memoria: $violations", violations.isEmpty())
    }

    private val invitationFiles = listOf(
        "data/remote/workspaces/RemoteWorkspaceInvitationsRepository.kt",
        "data/remote/workspaces/RemoteMyInvitationsRepository.kt",
        "data/remote/workspaces/InvitationsApiContract.kt",
        "data/repository/WorkspaceInvitationsRepository.kt",
        "data/repository/MyInvitationsRepository.kt",
        "domain/workspace/Invitation.kt",
        "domain/workspace/InvitationRules.kt",
    )

    @Test
    fun invitaciones_noUsanRoomNiDataStore() {
        val violations = invitationFiles.flatMap { path ->
            listOf("androidx.room", "androidx.datastore", "data.local", "SharedPreferences", "java.io.File")
                .filter { MainSources.kotlin(path).contains(it) }
                .map { "$path referencia $it" }
        }

        assertTrue("las invitaciones viven solo en memoria: $violations", violations.isEmpty())
    }

    @Test
    fun contratoDeInvitacionesNoExponeTiposRemotos() {
        listOf("data/repository/WorkspaceInvitationsRepository.kt", "data/repository/MyInvitationsRepository.kt").forEach {
            val contract = MainSources.kotlin(it)
            assertTrue(it, !contract.contains("data.remote") && !contract.contains("org.json") && !contract.contains("android."))
        }
    }

    @Test
    fun elManifiestoNoDeclaraEnlacesDeInvitacion() {
        val manifest = MainSources.manifest()

        assertTrue(!manifest.contains("android.intent.action.VIEW"))
        assertTrue(!manifest.contains("autoVerify") && !manifest.contains("<data"))
        assertTrue(!manifest.contains("BROWSABLE"))
    }

    @Test
    fun elEsquemaDeRoomSigueEnLaVersion15() {
        val schemas = MainSources.kotlinRoot.resolve("../../../../../../schemas").canonicalFile.walkTopDown()
            .filter { it.isFile && it.extension == "json" }.map(File::getName).toList()

        assertTrue(schemas.contains("15.json"))
        assertTrue("no debe haber esquema 16: $schemas", !schemas.contains("16.json"))
    }

    @Test
    fun contratoNoExponeTiposRemotos() {
        val contract = MainSources.kotlin("data/repository/WorkspaceRepository.kt")

        assertTrue(!contract.contains("data.remote") && !contract.contains("org.json") && !contract.contains("android."))
    }

    @Test
    fun contratoDeMiembrosNoExponeTiposRemotos() {
        val contract = MainSources.kotlin("data/repository/WorkspaceMembersRepository.kt")

        assertTrue(!contract.contains("data.remote") && !contract.contains("org.json") && !contract.contains("android."))
    }

    @Test
    fun laInterfazDeUsuarioNoConoceLaCapaRemotaNiLocal() {
        val files = MainSources.kotlinFiles("ui/workspaces")
        val violations = files.flatMap { file ->
            listOf("data.remote", "data.local").filter { file.readText().contains(it) }.map { "${file.name} referencia $it" }
        }

        assertTrue("ui/workspaces no debe conocer data.remote ni data.local: $violations", violations.isEmpty())
        assertTrue(files.isNotEmpty())
    }
}
