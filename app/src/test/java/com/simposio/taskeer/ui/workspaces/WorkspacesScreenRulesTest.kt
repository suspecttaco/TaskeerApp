package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.testing.workspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacesScreenRulesTest {

    @Test
    fun sinSesion_noHayAcceso() {
        assertFalse(WorkspacesScreenRules.showEntry(SessionState.SignedOut()))
        assertFalse(WorkspacesScreenRules.showEntry(SessionState.SignedOut(expired = true)))
        assertFalse(WorkspacesScreenRules.showEntry(SessionState.Resuming))
    }

    @Test
    fun conSesion_hayAcceso() {
        assertTrue(WorkspacesScreenRules.showEntry(SessionState.SignedIn(AccountSummary("a@x.com", "A"), offline = false)))
        assertTrue(WorkspacesScreenRules.showEntry(SessionState.SignedIn(null, offline = true)))
    }

    @Test
    fun acciones_ownerRenombraYElimina() {
        assertEquals(setOf(RowAction.RENAME, RowAction.DELETE), WorkspacesScreenRules.rowActions(workspace(role = WorkspaceRole.OWNER)))
    }

    @Test
    fun acciones_adminSoloRenombra() {
        assertEquals(setOf(RowAction.RENAME), WorkspacesScreenRules.rowActions(workspace(role = WorkspaceRole.ADMIN)))
    }

    @Test
    fun acciones_editorViewerYUnknown_ninguna() {
        listOf(WorkspaceRole.EDITOR, WorkspaceRole.VIEWER, WorkspaceRole.UNKNOWN).forEach {
            assertEquals(emptySet<RowAction>(), WorkspacesScreenRules.rowActions(workspace(role = it)))
        }
    }

    @Test
    fun acciones_personalNoElimina() {
        assertEquals(setOf(RowAction.RENAME), WorkspacesScreenRules.rowActions(workspace(isPersonal = true)))
    }

    @Test
    fun etiquetaDeRol_porRol() {
        val labels = WorkspaceRole.entries.map { WorkspacesScreenRules.roleLabel(workspace(role = it)) }

        assertEquals(
            listOf(RoleLabel.OWNER, RoleLabel.ADMIN, RoleLabel.EDITOR, RoleLabel.VIEWER, RoleLabel.MEMBER),
            labels,
        )
    }

    @Test
    fun etiquetaDeRol_personalPrevalece() {
        assertEquals(RoleLabel.PERSONAL, WorkspacesScreenRules.roleLabel(workspace(isPersonal = true)))
    }
}
