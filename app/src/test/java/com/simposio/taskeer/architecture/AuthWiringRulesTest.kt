package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthWiringRulesTest {

    private val root: File = MainSources.kotlinRoot

    private fun File.namesDataRemote(): Boolean =
        readText().let { it.contains("data.remote") || it.contains("data/remote") }

    @Test
    fun soloElPuenteFueraDeDataRemoteNombraDataRemote() {
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.startsWith(File(root, "data/remote")) }
            .filter { it.namesDataRemote() }
            .map { it.relativeTo(root).path }
            .toList()

        assertEquals(listOf("data/auth/AuthRepositoryFactory.kt"), offenders)
    }

    @Test
    fun dataAuthTieneUnSoloFicheroQueNombraDataRemote() {
        val files = MainSources.kotlinFiles("data/auth").filterNot { it.startsWith(File(root, "data/auth/google")) }

        assertEquals(listOf("AuthRepositoryFactory.kt"), files.map { it.name })
        assertEquals(1, files.count { it.namesDataRemote() })
    }

    @Test
    fun dataAuthGoogleNoNombraDataRemote() {
        val files = MainSources.kotlinFiles("data/auth/google")

        assertTrue(files.isNotEmpty())
        assertEquals(emptyList<String>(), files.filter { it.namesDataRemote() }.map { it.name })
    }

    @Test
    fun domainAuthNoImportaAndroidNiDataNiUi() {
        val forbidden = listOf("android.", "androidx.", "com.simposio.taskeer.data", "com.simposio.taskeer.ui", "com.simposio.taskeer.di")
        val violations = MainSources.kotlinFiles("domain/auth").flatMap { file ->
            file.readLines().map { it.trim() }.filter { it.startsWith("import ") }
                .map { it.removePrefix("import ") }
                .filter { imported -> forbidden.any { imported.startsWith(it) } }
                .map { "${file.name} importa $it" }
        }

        assertTrue("domain/auth debe ser Kotlin puro: $violations", violations.isEmpty())
        assertTrue(MainSources.kotlinFiles("domain/auth").isNotEmpty())
    }

    @Test
    fun contratoAuthRepositoryNoNombraDataRemote() {
        val source = MainSources.kotlin("data/repository/AuthRepository.kt")

        assertTrue(!source.contains("data.remote") && !source.contains("data/remote"))
    }

    @Test
    fun elContenedorSoloImportaElPuenteDataAuth() {
        val factory = MainSources.kotlin("di/AppContainerFactory.kt")

        assertTrue(factory.contains("import com.simposio.taskeer.data.auth.createRemoteRepositories"))
        assertTrue(!factory.contains("data.remote"))
    }

    @Test
    fun authEInbox_comparteElMismoApiClient() {
        val bridge = MainSources.kotlin("data/auth/AuthRepositoryFactory.kt")

        assertEquals(1, Regex("createApiClient\\(").findAll(bridge.substringAfter("fun createRemoteRepositories")).count())
        assertTrue(bridge.contains("RemoteAuthRepository(remote.client, remote.session, scope, remote.googleExchange)"))
        assertTrue(bridge.contains("RemoteNotificationInboxRepository(remote.client, auth.sessionState, scope)"))
        assertTrue(bridge.contains("RemoteNotificationLiveChannel(remote.client, remote.streamTransport, inbox, auth.sessionState, remote.config)"))
    }

    @Test
    fun workspaces_comparteElMismoApiClientYLaMismaSesion() {
        val bridge = MainSources.kotlin("data/auth/AuthRepositoryFactory.kt")

        assertTrue(bridge.contains("RemoteWorkspaceRepository(remote.client, auth.sessionState, scope)"))
        assertEquals(1, Regex("createApiClient\\(").findAll(bridge.substringAfter("fun createRemoteRepositories")).count())
    }

    @Test
    fun soloElPuenteConstruyeElRepositorioRemotoDeWorkspaces() {
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.startsWith(File(root, "data/remote")) }
            .filter { it.readText().contains("RemoteWorkspaceRepository(") }
            .map { it.relativeTo(root).path }
            .toList()

        assertEquals(listOf("data/auth/AuthRepositoryFactory.kt"), offenders)
    }

    @Test
    fun miembros_comparteElMismoApiClientYLaMismaSesion() {
        val bridge = MainSources.kotlin("data/auth/AuthRepositoryFactory.kt")

        assertTrue(bridge.contains("RemoteWorkspaceMembersRepository(remote.client, auth.sessionState, scope)"))
        assertEquals(1, Regex("createApiClient\\(").findAll(bridge.substringAfter("fun createRemoteRepositories")).count())
    }

    @Test
    fun soloElPuenteConstruyeElRepositorioRemotoDeMiembros() {
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.startsWith(File(root, "data/remote")) }
            .filter { it.readText().contains("RemoteWorkspaceMembersRepository(") }
            .map { it.relativeTo(root).path }
            .toList()

        assertEquals(listOf("data/auth/AuthRepositoryFactory.kt"), offenders)
    }

    @Test
    fun invitaciones_comparteElMismoApiClientYLaMismaSesion() {
        val bridge = MainSources.kotlin("data/auth/AuthRepositoryFactory.kt")

        assertTrue(bridge.contains("RemoteWorkspaceInvitationsRepository(remote.client, auth.sessionState, scope)"))
        assertTrue(bridge.contains("RemoteMyInvitationsRepository(remote.client, auth.sessionState, scope)"))
        assertEquals(1, Regex("createApiClient\\(").findAll(bridge.substringAfter("fun createRemoteRepositories")).count())
    }

    @Test
    fun soloElPuenteConstruyeLosRepositoriosRemotosDeInvitaciones() {
        listOf("RemoteWorkspaceInvitationsRepository(", "RemoteMyInvitationsRepository(").forEach { constructor ->
            val offenders = root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { it.startsWith(File(root, "data/remote")) }
                .filter { it.readText().contains(constructor) }
                .map { it.relativeTo(root).path }
                .toList()

            assertEquals(constructor, listOf("data/auth/AuthRepositoryFactory.kt"), offenders)
        }
    }

    @Test
    fun soloElPuenteConstruyeLosRepositoriosRemotos() {
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.startsWith(File(root, "data/remote")) }
            .filter { it.readText().contains("RemoteNotificationInboxRepository(") }
            .map { it.relativeTo(root).path }
            .toList()

        assertEquals(listOf("data/auth/AuthRepositoryFactory.kt"), offenders)
    }

    @Test
    fun elContenedorSoloImportaLaInterfazYLaFuncionDeGoogle() {
        val factory = MainSources.kotlin("di/AppContainerFactory.kt")
        val container = MainSources.kotlin("di/AppContainer.kt")

        assertTrue(factory.contains("import com.simposio.taskeer.data.auth.google.createGoogleCredentialSource"))
        assertTrue(!factory.contains("CredentialManagerGoogleSource") && !factory.contains("androidx.credentials"))
        assertTrue(container.contains("import com.simposio.taskeer.data.auth.google.GoogleCredentialSource"))
    }

    @Test
    fun tablerosDelServidor_comparteElMismoApiClientYLaMismaSesion() {
        val bridge = MainSources.kotlin("data/auth/AuthRepositoryFactory.kt")

        assertTrue(bridge.contains("RemoteServerBoardsRepository(remote.client, auth.sessionState, scope)"))
        assertTrue(bridge.contains("RemoteBoardAccessRepository(remote.client, auth.sessionState, scope)"))
        assertEquals(1, Regex("createApiClient\\(").findAll(bridge.substringAfter("fun createRemoteRepositories")).count())
    }

    @Test
    fun soloElPuenteConstruyeLosRepositoriosRemotosDeTableros() {
        listOf("RemoteServerBoardsRepository(", "RemoteBoardAccessRepository(").forEach { constructor ->
            val offenders = root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { it.startsWith(File(root, "data/remote")) }
                .filter { it.readText().contains(constructor) }
                .map { it.relativeTo(root).path }
                .toList()

            assertEquals(constructor, listOf("data/auth/AuthRepositoryFactory.kt"), offenders)
        }
    }
}
