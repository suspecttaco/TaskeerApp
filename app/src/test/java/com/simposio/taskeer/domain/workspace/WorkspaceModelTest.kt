package com.simposio.taskeer.domain.workspace

import com.simposio.taskeer.testing.MainSources
import com.simposio.taskeer.testing.boardOverride
import com.simposio.taskeer.testing.invitation
import com.simposio.taskeer.testing.member
import com.simposio.taskeer.testing.myInvitation
import com.simposio.taskeer.testing.serverBoard
import com.simposio.taskeer.testing.uuid
import com.simposio.taskeer.testing.workspace
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceModelTest {

    @Test
    fun sinDependenciasDeAndroid() {
        val files = MainSources.kotlinFiles("domain/workspace")
        val forbidden = listOf("android.", "androidx.", "com.simposio.taskeer.data", "com.simposio.taskeer.ui", "org.json")

        val violations = files.flatMap { file ->
            file.readLines().map { it.trim() }.filter { it.startsWith("import ") }
                .map { it.removePrefix("import ") }
                .filter { imported -> forbidden.any { imported.startsWith(it) } }
                .map { "${file.name} importa $it" }
        }

        assertTrue("domain/workspace debe ser Kotlin puro: $violations", violations.isEmpty())
        assertTrue(files.isNotEmpty())
    }

    @Test
    fun toString_noIncluyeElNombre() {
        assertFalse(workspace(name = "Secreto").toString().contains("Secreto"))
    }

    @Test
    fun miembro_sinDependenciasDeAndroid() {
        val source = MainSources.kotlin("domain/workspace/WorkspaceMember.kt")
        val imports = source.lines().filter { it.startsWith("import ") }

        assertTrue(imports.none { it.startsWith("import android") || it.startsWith("import org.json") })
        assertTrue(imports.isNotEmpty())
    }

    @Test
    fun miembro_toString_noIncluyeDatosPersonales() {
        val text = member(1, name = "Secreto Nombre", email = "secreto@example.com").toString()

        assertFalse(text.contains("Secreto") || text.contains("secreto@") || text.contains(uuid(1)))
    }

    @Test
    fun invitacion_sinDependenciasDeAndroid() {
        val source = MainSources.kotlin("domain/workspace/Invitation.kt") + MainSources.kotlin("domain/workspace/InvitationRules.kt")
        val imports = source.lines().filter { it.startsWith("import ") }

        assertTrue(imports.none { it.startsWith("import android") || it.startsWith("import org.json") })
        assertTrue(imports.none { it.startsWith("import com.simposio.taskeer.data") || it.startsWith("import com.simposio.taskeer.ui") })
        assertTrue(imports.isNotEmpty())
    }

    @Test
    fun invitacion_toString_noIncluyeDatosPersonales() {
        val text = invitation(1, email = "secreto@example.com").toString()

        assertFalse(text.contains("secreto@") || text.contains(uuid(501)) || text.contains(uuid(1)))
    }

    @Test
    fun miInvitacion_toString_noIncluyeDatosPersonales() {
        val text = myInvitation(1, workspaceName = "Equipo Secreto").toString()

        assertFalse(text.contains("Secreto") || text.contains(uuid(601)) || text.contains(uuid(701)))
    }

    @Test
    fun aceptacion_toString_noIncluyeElNombre() {
        assertFalse(AcceptOutcome.Accepted("Equipo Secreto", alreadyMember = false).toString().contains("Secreto"))
    }

    @Test
    fun sobreDeTableros_sinDependenciasDeAndroid() {
        val imports = listOf("ServerBoard.kt", "BoardMemberOverride.kt", "BoardRoleRules.kt")
            .flatMap { MainSources.kotlin("domain/workspace/$it").lines().filter { line -> line.startsWith("import ") } }

        assertTrue(imports.none { it.startsWith("import android") || it.startsWith("import org.json") })
        assertTrue(imports.none { it.startsWith("import com.simposio.taskeer.data") || it.startsWith("import com.simposio.taskeer.ui") })
        assertTrue(imports.isNotEmpty())
    }

    @Test
    fun override_toString_noIncluyeElUserId() {
        assertFalse(boardOverride(3).toString().contains(uuid(3)))
    }

    @Test
    fun accesoATablero_toString_noIncluyeElNombre() {
        val state = BoardAccessState(boardName = "Tablero Secreto", boardId = uuid(1001), effectiveRole = WorkspaceRole.EDITOR)

        assertFalse(state.toString().contains("Secreto") || state.toString().contains(uuid(1001)))
        assertFalse(serverBoard(1, "Tablero Secreto").toString().contains("Secreto"))
    }
}
