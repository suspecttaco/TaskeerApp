package com.simposio.taskeer.domain.workspace

import com.simposio.taskeer.testing.member
import com.simposio.taskeer.testing.workspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberRulesTest {

    private val owner = member(1, WorkspaceRole.OWNER)
    private val admin = member(2, WorkspaceRole.ADMIN)
    private val editor = member(3, WorkspaceRole.EDITOR)
    private val viewer = member(4, WorkspaceRole.VIEWER)
    private val unknown = member(5, WorkspaceRole.UNKNOWN)

    @Test
    fun ordena_porRolLuegoNombreLuegoId() {
        val items = listOf(
            unknown,
            viewer,
            member(7, WorkspaceRole.EDITOR, name = "beto"),
            member(6, WorkspaceRole.EDITOR, name = "Ana"),
            admin,
            owner,
        )

        val result = MemberRules.sorted(items).map { it.role to it.displayName }

        assertEquals(
            listOf(
                WorkspaceRole.OWNER to owner.displayName,
                WorkspaceRole.ADMIN to admin.displayName,
                WorkspaceRole.EDITOR to "Ana",
                WorkspaceRole.EDITOR to "beto",
                WorkspaceRole.VIEWER to viewer.displayName,
                WorkspaceRole.UNKNOWN to unknown.displayName,
            ),
            result,
        )
    }

    @Test
    fun ordena_aIgualdadDeNombre_porUserId() {
        val first = member(8, name = "Igual")
        val second = member(9, name = "igual")

        assertEquals(listOf(first, second), MemberRules.sorted(listOf(second, first)))
    }

    @Test
    fun selfOf_coincideCorreoSinMayusculas() {
        val items = listOf(owner, member(3, email = "Ana@Example.com"))

        assertEquals(uuidOf(3), MemberRules.selfOf(items, "  ana@example.COM ")?.userId)
    }

    @Test
    fun selfOf_sinCoincidencia_esNull() {
        assertNull(MemberRules.selfOf(listOf(owner), "otro@example.com"))
        assertNull(MemberRules.selfOf(listOf(owner), null))
        assertNull(MemberRules.selfOf(listOf(owner), "  "))
    }

    @Test
    fun effectiveRole_prefiereLaEntradaPropia() {
        val items = listOf(owner, member(3, WorkspaceRole.VIEWER, email = "yo@example.com"))

        val role = MemberRules.effectiveRole(workspace(role = WorkspaceRole.OWNER), items, "yo@example.com")

        assertEquals(WorkspaceRole.VIEWER, role)
    }

    @Test
    fun effectiveRole_sinEntrada_usaMyRole() {
        val role = MemberRules.effectiveRole(workspace(role = WorkspaceRole.ADMIN), emptyList(), "yo@example.com")

        assertEquals(WorkspaceRole.ADMIN, role)
    }

    @Test
    fun canManage_ownerYAdmin_si() {
        assertTrue(MemberRules.canManageMembers(WorkspaceRole.OWNER))
        assertTrue(MemberRules.canManageMembers(WorkspaceRole.ADMIN))
    }

    @Test
    fun canManage_editorViewerUnknown_no() {
        assertFalse(MemberRules.canManageMembers(WorkspaceRole.EDITOR))
        assertFalse(MemberRules.canManageMembers(WorkspaceRole.VIEWER))
        assertFalse(MemberRules.canManageMembers(WorkspaceRole.UNKNOWN))
    }

    @Test
    fun canChangeRole_gestorSobreNoOwner_si() {
        listOf(WorkspaceRole.OWNER, WorkspaceRole.ADMIN).forEach { actor ->
            listOf(admin, editor, viewer).forEach { target ->
                assertTrue(MemberRules.canChangeRole(actor, target, self = null))
            }
        }
    }

    @Test
    fun canChangeRole_sobreOwner_no() {
        assertFalse(MemberRules.canChangeRole(WorkspaceRole.OWNER, owner, self = null))
        assertFalse(MemberRules.canChangeRole(WorkspaceRole.ADMIN, unknown, self = null))
    }

    @Test
    fun canChangeRole_sobreUnoMismo_no() {
        assertFalse(MemberRules.canChangeRole(WorkspaceRole.ADMIN, admin, self = admin))
    }

    @Test
    fun canChangeRole_sinGestor_no() {
        assertFalse(MemberRules.canChangeRole(WorkspaceRole.EDITOR, viewer, self = null))
        assertFalse(MemberRules.canChangeRole(WorkspaceRole.UNKNOWN, viewer, self = null))
    }

    @Test
    fun assignableRoles_excluyeOwnerYElActual() {
        assertEquals(listOf(WorkspaceRole.ADMIN, WorkspaceRole.VIEWER), MemberRules.assignableRoles(editor))
        assertFalse(WorkspaceRole.OWNER in MemberRules.assignableRoles(viewer))
    }

    @Test
    fun canRemove_gestorSobreNoOwner_si() {
        assertTrue(MemberRules.canRemove(WorkspaceRole.OWNER, editor, self = null))
        assertTrue(MemberRules.canRemove(WorkspaceRole.ADMIN, admin, self = null))
    }

    @Test
    fun canRemove_sobreOwner_no() {
        assertFalse(MemberRules.canRemove(WorkspaceRole.ADMIN, owner, self = null))
        assertFalse(MemberRules.canRemove(WorkspaceRole.ADMIN, unknown, self = null))
    }

    @Test
    fun canRemove_sobreUnoMismo_no() {
        assertFalse(MemberRules.canRemove(WorkspaceRole.ADMIN, admin, self = admin))
    }

    @Test
    fun canRemove_sinGestor_no() {
        assertFalse(MemberRules.canRemove(WorkspaceRole.VIEWER, editor, self = null))
    }

    @Test
    fun canLeave_noOwnerNoPersonal_si() {
        listOf(WorkspaceRole.ADMIN, WorkspaceRole.EDITOR, WorkspaceRole.VIEWER).forEach {
            assertTrue(MemberRules.canLeave(it, isPersonal = false, self = editor))
        }
    }

    @Test
    fun canLeave_owner_no() {
        assertFalse(MemberRules.canLeave(WorkspaceRole.OWNER, isPersonal = false, self = owner))
        assertFalse(MemberRules.canLeave(WorkspaceRole.UNKNOWN, isPersonal = false, self = unknown))
    }

    @Test
    fun canLeave_personal_no() {
        assertFalse(MemberRules.canLeave(WorkspaceRole.EDITOR, isPersonal = true, self = editor))
    }

    @Test
    fun canLeave_sinIdentificarme_no() {
        assertFalse(MemberRules.canLeave(WorkspaceRole.EDITOR, isPersonal = false, self = null))
    }

    @Test
    fun canTransfer_soloOwner() {
        assertTrue(MemberRules.canTransferOwnership(WorkspaceRole.OWNER, admin, owner, isPersonal = false))
        assertFalse(MemberRules.canTransferOwnership(WorkspaceRole.ADMIN, editor, admin, isPersonal = false))
    }

    @Test
    fun canTransfer_aOwnerOUnknown_no() {
        assertFalse(MemberRules.canTransferOwnership(WorkspaceRole.OWNER, owner, null, isPersonal = false))
        assertFalse(MemberRules.canTransferOwnership(WorkspaceRole.OWNER, unknown, null, isPersonal = false))
    }

    @Test
    fun canTransfer_aUnoMismo_no() {
        assertFalse(MemberRules.canTransferOwnership(WorkspaceRole.OWNER, admin, admin, isPersonal = false))
    }

    @Test
    fun canTransfer_personal_no() {
        assertFalse(MemberRules.canTransferOwnership(WorkspaceRole.OWNER, admin, owner, isPersonal = true))
    }

    @Test
    fun reapply_quitaLosExpulsados() {
        val result = MemberRules.reapply(listOf(owner, editor, viewer), MemberConfirmedOps(removedIds = setOf(editor.userId)))

        assertEquals(listOf(owner, viewer), result)
    }

    @Test
    fun reapply_reaplicaLosRoles() {
        val result = MemberRules.reapply(
            listOf(owner, editor),
            MemberConfirmedOps(roles = mapOf(editor.userId to WorkspaceRole.ADMIN)),
        )

        assertEquals(listOf(owner, editor.copy(role = WorkspaceRole.ADMIN)), result)
    }

    private fun uuidOf(n: Int) = member(n).userId
}
