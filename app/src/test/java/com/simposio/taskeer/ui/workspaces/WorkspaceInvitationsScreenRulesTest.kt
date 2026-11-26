package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.workspace.InvitationStatus
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.testing.invitation
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceInvitationsScreenRulesTest {

    private val now = Instant.parse("2026-11-26T10:00:00Z")

    @Test
    fun acciones_ownerSobrePendiente_revocar() {
        assertEquals(setOf(InvitationAction.REVOKE), WorkspaceInvitationsScreenRules.actions(WorkspaceRole.OWNER, invitation(1), now))
    }

    @Test
    fun acciones_adminSobrePendiente_revocar() {
        assertEquals(setOf(InvitationAction.REVOKE), WorkspaceInvitationsScreenRules.actions(WorkspaceRole.ADMIN, invitation(1), now))
    }

    @Test
    fun acciones_sobreVencida_ninguna() {
        val expired = invitation(1, expiresAt = Instant.parse("2026-11-01T10:00:00Z"))

        assertTrue(WorkspaceInvitationsScreenRules.actions(WorkspaceRole.OWNER, expired, now).isEmpty())
        assertTrue(
            WorkspaceInvitationsScreenRules.actions(WorkspaceRole.OWNER, invitation(2, status = InvitationStatus.REVOKED), now).isEmpty(),
        )
    }

    @Test
    fun acciones_editorViewerUnknown_seccionOculta() {
        listOf(WorkspaceRole.EDITOR, WorkspaceRole.VIEWER, WorkspaceRole.UNKNOWN).forEach {
            assertFalse(WorkspaceInvitationsScreenRules.sectionVisible(it, isPersonal = false))
        }
    }

    @Test
    fun acciones_personal_seccionOculta() {
        assertFalse(WorkspaceInvitationsScreenRules.sectionVisible(WorkspaceRole.OWNER, isPersonal = true))
        assertTrue(WorkspaceInvitationsScreenRules.sectionVisible(WorkspaceRole.OWNER, isPersonal = false))
    }
}
