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
