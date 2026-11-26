package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.workspace.WorkspaceRole
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Test

class WorkspaceInvitationsUiStateTest {

    @Test
    fun modelosDeUi_toString_noIncluyenDatosPersonales() {
        val now = Instant.parse("2026-11-26T10:00:00Z")
        val texts = listOf(
            InvitationRowUi("id-secreto", "secreto@example.com", RoleLabel.VIEWER, now, false, emptySet()),
            InvitationDialog.Invite("secreto@example.com", WorkspaceRole.VIEWER),
            InvitationDialog.VerifyEmail("secreto@example.com", WorkspaceRole.VIEWER),
            InvitationDialog.ConfirmRevoke("id-secreto", "secreto@example.com"),
            InvitationNotice.Invited("secreto@example.com"),
            WorkspaceInvitationsUiState(
                rows = listOf(InvitationRowUi("id-secreto", "secreto@example.com", RoleLabel.VIEWER, now, false, emptySet())),
                dialog = InvitationDialog.Invite("secreto@example.com", WorkspaceRole.VIEWER),
            ),
            MyInvitationRowUi("id-secreto", "Equipo Secreto", RoleLabel.EDITOR, now, false, true, false),
            InboxNotice.Joined("Equipo Secreto"),
            InboxNotice.AlreadyMember("Equipo Secreto"),
            MyInvitationsUiState(rows = listOf(MyInvitationRowUi("id-secreto", "Equipo Secreto", RoleLabel.EDITOR, now, false, true, false))),
        ).map { it.toString() }

        texts.forEach { assertFalse(it, it.contains("ecreto")) }
    }
}
