package com.simposio.taskeer.architecture

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceSecretsRulesTest {

    @Test
    fun sinLogsDeContenido() {
        val files = MainSources.kotlinFiles("data/remote/workspaces") +
            MainSources.kotlinFiles("ui/workspaces") +
            MainSources.kotlinFiles("domain/workspace")
        val logging = Regex("""\bLog\.[a-z]\(|\bprintln\(|\bprint\(|Timber\.|printStackTrace""")

        val offenders = files.filter { logging.containsMatchIn(it.readText()) }.map { it.name }

        assertTrue("los nombres de workspace son datos de usuario; sin logs: $offenders", offenders.isEmpty())
        assertTrue(files.isNotEmpty())
    }

    @Test
    fun miembros_sinLogsDeContenido() {
        val files = listOf(
            "data/remote/workspaces/RemoteWorkspaceMembersRepository.kt",
            "data/remote/workspaces/WorkspaceMembersApiContract.kt",
            "domain/workspace/WorkspaceMember.kt",
            "domain/workspace/MemberRules.kt",
            "ui/workspaces/WorkspaceMembersViewModel.kt",
            "ui/workspaces/WorkspaceMembersScreen.kt",
            "ui/workspaces/WorkspaceMemberDialogs.kt",
        )
        val logging = Regex("""\bLog\.[a-z]\(|\bprintln\(|\bprint\(|Timber\.|printStackTrace""")

        val offenders = files.filter { logging.containsMatchIn(MainSources.kotlin(it)) }

        assertTrue("correo, nombre y userId de un miembro son datos personales; sin logs: $offenders", offenders.isEmpty())
    }

    @Test
    fun invitaciones_sinLogsDeContenido() {
        val files = listOf(
            "data/remote/workspaces/RemoteWorkspaceInvitationsRepository.kt",
            "data/remote/workspaces/RemoteMyInvitationsRepository.kt",
            "data/remote/workspaces/InvitationsApiContract.kt",
            "domain/workspace/Invitation.kt",
            "domain/workspace/InvitationRules.kt",
            "ui/workspaces/WorkspaceInvitationsViewModel.kt",
            "ui/workspaces/WorkspaceInvitationsUiState.kt",
            "ui/workspaces/WorkspaceInvitationsSection.kt",
            "ui/workspaces/WorkspaceInvitationDialogs.kt",
            "ui/workspaces/MyInvitationsViewModel.kt",
            "ui/workspaces/MyInvitationsScreen.kt",
        )
        val logging = Regex("""\bLog\.[a-z]\(|\bprintln\(|\bprint\(|Timber\.|printStackTrace""")

        val offenders = files.filter { logging.containsMatchIn(MainSources.kotlin(it)) }

        assertTrue("correo, nombre de workspace y token son datos sensibles; sin logs: $offenders", offenders.isEmpty())
    }

    @Test
    fun elRepositorioDeInvitacionesNoEscribeDatosEnMensajesDeError() {
        listOf(
            "data/remote/workspaces/RemoteWorkspaceInvitationsRepository.kt",
            "data/remote/workspaces/RemoteMyInvitationsRepository.kt",
        ).forEach {
            assertTrue(it, !Regex("""DomainError\.\w+\([^)]*\$""").containsMatchIn(MainSources.kotlin(it)))
        }
    }

    @Test
    fun miembro_noInterpolaDatosPersonalesEnSuTexto() {
        val toString = MainSources.kotlin("domain/workspace/WorkspaceMember.kt").substringAfter("override fun toString()")
            .substringBefore("data class MembersState")

        assertTrue(!toString.contains("\$email") && !toString.contains("\$displayName") && !toString.contains("\$userId"))
    }

    @Test
    fun elRepositorioDeMiembrosNoEscribeDatosEnMensajesDeError() {
        val source = MainSources.kotlin("data/remote/workspaces/RemoteWorkspaceMembersRepository.kt")

        assertTrue(!Regex("""DomainError\.\w+\([^)]*\$""").containsMatchIn(source))
    }

    @Test
    fun workspace_noInterpolaElNombreEnSuTexto() {
        val source = MainSources.kotlin("domain/workspace/Workspace.kt")
        val toString = source.substringAfter("override fun toString()")

        assertTrue(!toString.contains("\$name") && !toString.contains("\${name}"))
    }

    @Test
    fun elRepositorioNoEscribeNombresEnMensajesDeError() {
        val source = MainSources.kotlin("data/remote/workspaces/RemoteWorkspaceRepository.kt")

        assertTrue(!Regex("""DomainError\.\w+\([^)]*\$""").containsMatchIn(source))
    }
}
