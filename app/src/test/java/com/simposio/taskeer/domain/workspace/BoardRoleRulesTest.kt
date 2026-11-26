package com.simposio.taskeer.domain.workspace

import com.simposio.taskeer.testing.boardOverride
import com.simposio.taskeer.testing.member
import com.simposio.taskeer.testing.serverBoard
import com.simposio.taskeer.testing.uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardRoleRulesTest {

    private val allRoles = WorkspaceRole.entries

    @Test
    fun canView_ownerAdminEditorViewer_si() {
        listOf(WorkspaceRole.OWNER, WorkspaceRole.ADMIN, WorkspaceRole.EDITOR, WorkspaceRole.VIEWER)
            .forEach { assertTrue(it.name, BoardRoleRules.canView(it)) }
    }

    @Test
    fun canView_unknownYNull_no() {
        assertFalse(BoardRoleRules.canView(WorkspaceRole.UNKNOWN))
        assertFalse(BoardRoleRules.canView(null))
    }

    @Test
    fun canEditTasks_ownerAdminEditor_si() {
        listOf(WorkspaceRole.OWNER, WorkspaceRole.ADMIN, WorkspaceRole.EDITOR)
            .forEach { assertTrue(it.name, BoardRoleRules.canEditTasks(it)) }
    }

    @Test
    fun canEditTasks_viewerUnknownNull_no() {
        assertFalse(BoardRoleRules.canEditTasks(WorkspaceRole.VIEWER))
        assertFalse(BoardRoleRules.canEditTasks(WorkspaceRole.UNKNOWN))
        assertFalse(BoardRoleRules.canEditTasks(null))
    }

    @Test
    fun canManageColumns_soloOwnerYAdmin() {
        val allowed = allRoles.filter { BoardRoleRules.canManageColumns(it) }

        assertEquals(listOf(WorkspaceRole.OWNER, WorkspaceRole.ADMIN), allowed)
        assertFalse(BoardRoleRules.canManageColumns(null))
    }

    @Test
    fun capabilities_porRol() {
        assertEquals(
            listOf(BoardCapability.VIEW, BoardCapability.EDIT_TASKS, BoardCapability.MANAGE_COLUMNS),
            BoardRoleRules.capabilities(WorkspaceRole.ADMIN),
        )
        assertEquals(
            listOf(BoardCapability.VIEW, BoardCapability.EDIT_TASKS),
            BoardRoleRules.capabilities(WorkspaceRole.EDITOR),
        )
        assertEquals(listOf(BoardCapability.VIEW), BoardRoleRules.capabilities(WorkspaceRole.VIEWER))
    }

    @Test
    fun capabilities_unknownYNull_vacia() {
        assertEquals(emptyList<BoardCapability>(), BoardRoleRules.capabilities(WorkspaceRole.UNKNOWN))
        assertEquals(emptyList<BoardCapability>(), BoardRoleRules.capabilities(null))
    }

    @Test
    fun canManageOverrides_ownerYAdmin_si() {
        assertTrue(BoardRoleRules.canManageOverrides(WorkspaceRole.OWNER))
        assertTrue(BoardRoleRules.canManageOverrides(WorkspaceRole.ADMIN))
    }

    @Test
    fun canManageOverrides_editorViewerUnknown_no() {
        assertFalse(BoardRoleRules.canManageOverrides(WorkspaceRole.EDITOR))
        assertFalse(BoardRoleRules.canManageOverrides(WorkspaceRole.VIEWER))
        assertFalse(BoardRoleRules.canManageOverrides(WorkspaceRole.UNKNOWN))
    }

    @Test
    fun canManageOverrides_ignoraElRolEfectivoDelTablero() {
        val workspaceRole = WorkspaceRole.ADMIN
        val effectiveOnBoard = WorkspaceRole.VIEWER

        assertTrue(BoardRoleRules.canManageOverrides(workspaceRole))
        assertFalse(BoardRoleRules.canEditTasks(effectiveOnBoard))
        assertTrue(BoardRoleRules.canSetOverride(workspaceRole, member(3), member(2, WorkspaceRole.ADMIN)))
    }

    @Test
    fun canSetOverride_gestorSobreNoOwner_si() {
        val self = member(2, WorkspaceRole.ADMIN)

        listOf(WorkspaceRole.ADMIN, WorkspaceRole.EDITOR, WorkspaceRole.VIEWER).forEach { role ->
            assertTrue(role.name, BoardRoleRules.canSetOverride(WorkspaceRole.OWNER, member(3, role), self))
        }
    }

    @Test
    fun canSetOverride_sobreOwnerOUnknown_no() {
        val self = member(2, WorkspaceRole.ADMIN)

        assertFalse(BoardRoleRules.canSetOverride(WorkspaceRole.ADMIN, member(1, WorkspaceRole.OWNER), self))
        assertFalse(BoardRoleRules.canSetOverride(WorkspaceRole.ADMIN, member(3, WorkspaceRole.UNKNOWN), self))
    }

    @Test
    fun canSetOverride_sobreUnoMismo_no() {
        val self = member(2, WorkspaceRole.ADMIN)

        assertFalse(BoardRoleRules.canSetOverride(WorkspaceRole.ADMIN, self, self))
    }

    @Test
    fun canSetOverride_sinGestor_no() {
        listOf(WorkspaceRole.EDITOR, WorkspaceRole.VIEWER, WorkspaceRole.UNKNOWN).forEach {
            assertFalse(it.name, BoardRoleRules.canSetOverride(it, member(3), member(2)))
        }
    }

    @Test
    fun canRemoveOverride_conOverride_si() {
        assertTrue(BoardRoleRules.canRemoveOverride(WorkspaceRole.ADMIN, member(3), member(2), boardOverride(3)))
    }

    @Test
    fun canRemoveOverride_sinOverride_no() {
        assertFalse(BoardRoleRules.canRemoveOverride(WorkspaceRole.ADMIN, member(3), member(2), null))
    }

    @Test
    fun canRemoveOverride_sobreUnoMismo_no() {
        val self = member(2, WorkspaceRole.ADMIN)

        assertFalse(BoardRoleRules.canRemoveOverride(WorkspaceRole.ADMIN, self, self, boardOverride(2)))
    }

    @Test
    fun assignableBoardRoles_excluyeOwnerYElActual() {
        assertEquals(
            listOf(WorkspaceRole.ADMIN, WorkspaceRole.VIEWER),
            BoardRoleRules.assignableBoardRoles(WorkspaceRole.EDITOR),
        )
        assertFalse(WorkspaceRole.OWNER in BoardRoleRules.assignableBoardRoles(WorkspaceRole.VIEWER))
    }

    @Test
    fun ordenaTablerosPorNombre() {
        val sorted = BoardRoleRules.sortedBoards(
            listOf(serverBoard(1, "beta"), serverBoard(2, "Alfa"), serverBoard(3, "alfa")),
        )

        assertEquals(listOf("Alfa", "alfa", "beta"), sorted.map { it.name })
        assertEquals(listOf(uuid(1002), uuid(1003)), sorted.take(2).map { it.id })
    }

    private val members = listOf(
        member(1, WorkspaceRole.OWNER),
        member(2, WorkspaceRole.ADMIN),
        member(3, WorkspaceRole.EDITOR),
        member(4, WorkspaceRole.VIEWER),
    )

    @Test
    fun filas_unaPorMiembroSinOwnerNiYo() {
        val rows = BoardRoleRules.rows(members, emptyList(), self = members[1])

        assertEquals(listOf(uuid(3), uuid(4)), rows.map { it.member.userId })
    }

    @Test
    fun filas_overrideDeNoMiembro_seOmite() {
        val rows = BoardRoleRules.rows(members, listOf(boardOverride(9)), self = members[1])

        assertEquals(listOf(uuid(3), uuid(4)), rows.map { it.member.userId })
        assertTrue(rows.all { it.override == null })
    }

    @Test
    fun filas_sinOverride_esIgualQueElWorkspace() {
        val row = BoardRoleRules.rows(members, emptyList(), self = members[1]).first()

        assertNull(row.override)
        assertEquals(WorkspaceRole.EDITOR, row.boardRole)
    }

    @Test
    fun filas_conOverride_usaElRolDelTablero() {
        val row = BoardRoleRules.rows(members, listOf(boardOverride(3, WorkspaceRole.ADMIN)), self = members[1]).first()

        assertEquals(WorkspaceRole.ADMIN, row.boardRole)
        assertEquals(WorkspaceRole.EDITOR, row.member.role)
    }

    @Test
    fun filas_overrideConRolDesconocido_seMuestraYSePuedeQuitar() {
        val self = members[1]
        val row = BoardRoleRules.rows(members, listOf(boardOverride(3, WorkspaceRole.UNKNOWN)), self).first()

        assertEquals(WorkspaceRole.UNKNOWN, row.boardRole)
        assertTrue(BoardRoleRules.canRemoveOverride(WorkspaceRole.ADMIN, row.member, self, row.override))
    }

    @Test
    fun reapply_upsert_ganaAlRefrescoAnterior() {
        val fresh = listOf(boardOverride(3, WorkspaceRole.VIEWER), boardOverride(4, WorkspaceRole.VIEWER))
        val confirmed = BoardOverrideConfirmedOps(upserted = mapOf(uuid(3) to boardOverride(3, WorkspaceRole.ADMIN)))

        val result = BoardRoleRules.reapply(fresh, confirmed)

        assertEquals(WorkspaceRole.ADMIN, result.first { it.userId == uuid(3) }.role)
        assertEquals(2, result.size)
    }

    @Test
    fun reapply_upsert_insertaLoQueFaltabaEnElRefresco() {
        val confirmed = BoardOverrideConfirmedOps(upserted = mapOf(uuid(3) to boardOverride(3)))

        assertEquals(listOf(uuid(3)), BoardRoleRules.reapply(emptyList(), confirmed).map { it.userId })
    }

    @Test
    fun reapply_quitado_noReinsertaElOverride() {
        val confirmed = BoardOverrideConfirmedOps(removedUserIds = setOf(uuid(3)))

        val result = BoardRoleRules.reapply(listOf(boardOverride(3), boardOverride(4)), confirmed)

        assertEquals(listOf(uuid(4)), result.map { it.userId })
    }

    @Test
    fun avisoDeCambio_soloSiDifiereYHabiaRol() {
        assertEquals(WorkspaceRole.VIEWER, BoardRoleRules.roleChangeNotice(WorkspaceRole.EDITOR, WorkspaceRole.VIEWER))
        assertNull(BoardRoleRules.roleChangeNotice(WorkspaceRole.EDITOR, WorkspaceRole.EDITOR))
        assertNull(BoardRoleRules.roleChangeNotice(null, WorkspaceRole.EDITOR))
        assertNull(BoardRoleRules.roleChangeNotice(WorkspaceRole.EDITOR, null))
    }
}
