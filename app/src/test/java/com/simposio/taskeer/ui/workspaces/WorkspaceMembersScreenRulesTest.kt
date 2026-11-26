package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.workspace.WorkspaceMember
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.testing.member
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceMembersScreenRulesTest {

    private val owner = member(1, WorkspaceRole.OWNER)
    private val admin = member(2, WorkspaceRole.ADMIN)
    private val editor = member(3, WorkspaceRole.EDITOR)
    private val viewer = member(4, WorkspaceRole.VIEWER)
    private val unknown = member(5, WorkspaceRole.UNKNOWN)

    private fun actions(actor: WorkspaceRole, target: WorkspaceMember, self: WorkspaceMember?, personal: Boolean = false) =
        WorkspaceMembersScreenRules.actions(actor, target, self, personal)

    @Test
    fun acciones_ownerSobreNoOwner_cambiaRolExpulsaTransfiere() {
        assertEquals(
            setOf(MemberAction.CHANGE_ROLE, MemberAction.REMOVE, MemberAction.TRANSFER),
            actions(WorkspaceRole.OWNER, editor, owner),
        )
    }

    @Test
    fun acciones_adminSobreNoOwner_cambiaRolYExpulsa_noTransfiere() {
        assertEquals(setOf(MemberAction.CHANGE_ROLE, MemberAction.REMOVE), actions(WorkspaceRole.ADMIN, viewer, admin))
    }

    @Test
    fun acciones_editorViewerUnknown_soloSalirEnLaPropia() {
        assertEquals(emptySet<MemberAction>(), actions(WorkspaceRole.EDITOR, viewer, editor))
        assertEquals(setOf(MemberAction.LEAVE), actions(WorkspaceRole.EDITOR, editor, editor))
        assertEquals(setOf(MemberAction.LEAVE), actions(WorkspaceRole.VIEWER, viewer, viewer))
        assertEquals(emptySet<MemberAction>(), actions(WorkspaceRole.UNKNOWN, unknown, unknown))
    }

    @Test
    fun acciones_ownerEnSuFila_ninguna() {
        assertEquals(emptySet<MemberAction>(), actions(WorkspaceRole.OWNER, owner, owner))
    }

    @Test
    fun acciones_sobreElOwner_ninguna() {
        assertEquals(emptySet<MemberAction>(), actions(WorkspaceRole.ADMIN, owner, admin))
    }

    @Test
    fun acciones_personal_sinSalirNiTransferir() {
        assertEquals(emptySet<MemberAction>(), actions(WorkspaceRole.EDITOR, editor, editor, personal = true))
        assertEquals(
            setOf(MemberAction.CHANGE_ROLE, MemberAction.REMOVE),
            actions(WorkspaceRole.OWNER, editor, owner, personal = true),
        )
    }

    @Test
    fun acciones_sinIdentificarme_noOfreceSalir() {
        assertEquals(emptySet<MemberAction>(), actions(WorkspaceRole.EDITOR, editor, self = null))
    }

    @Test
    fun pistaDelOwner_soloSiEsOwnerYNoPersonal() {
        assertTrue(WorkspaceMembersScreenRules.ownerHint(WorkspaceRole.OWNER, isPersonal = false))
        assertFalse(WorkspaceMembersScreenRules.ownerHint(WorkspaceRole.OWNER, isPersonal = true))
        assertFalse(WorkspaceMembersScreenRules.ownerHint(WorkspaceRole.ADMIN, isPersonal = false))
    }

    @Test
    fun etiquetaDeRol_unaPorRol() {
        assertEquals(RoleLabel.OWNER, WorkspaceMembersScreenRules.roleLabel(WorkspaceRole.OWNER))
        assertEquals(RoleLabel.ADMIN, WorkspaceMembersScreenRules.roleLabel(WorkspaceRole.ADMIN))
        assertEquals(RoleLabel.EDITOR, WorkspaceMembersScreenRules.roleLabel(WorkspaceRole.EDITOR))
        assertEquals(RoleLabel.VIEWER, WorkspaceMembersScreenRules.roleLabel(WorkspaceRole.VIEWER))
        assertEquals(RoleLabel.MEMBER, WorkspaceMembersScreenRules.roleLabel(WorkspaceRole.UNKNOWN))
    }
}
