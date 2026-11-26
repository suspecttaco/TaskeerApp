package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.workspace.BoardCapability
import com.simposio.taskeer.domain.workspace.BoardRoleRules
import com.simposio.taskeer.domain.workspace.OverrideRow
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.testing.boardOverride
import com.simposio.taskeer.testing.member
import com.simposio.taskeer.testing.uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardAccessScreenRulesTest {

    private val self = member(2, WorkspaceRole.ADMIN)

    @Test
    fun capacidades_owner_verEditarYColumnas() {
        assertEquals(
            listOf(BoardCapability.VIEW, BoardCapability.EDIT_TASKS, BoardCapability.MANAGE_COLUMNS),
            BoardAccessScreenRules.capabilities(WorkspaceRole.OWNER),
        )
    }

    @Test
    fun capacidades_admin_verEditarYColumnas() {
        assertEquals(3, BoardAccessScreenRules.capabilities(WorkspaceRole.ADMIN).size)
    }

    @Test
    fun capacidades_editor_verYEditar() {
        assertEquals(
            listOf(BoardCapability.VIEW, BoardCapability.EDIT_TASKS),
            BoardAccessScreenRules.capabilities(WorkspaceRole.EDITOR),
        )
    }

    @Test
    fun capacidades_viewer_soloVer() {
        assertEquals(listOf(BoardCapability.VIEW), BoardAccessScreenRules.capabilities(WorkspaceRole.VIEWER))
    }

    @Test
    fun rolNoDisponible_sinCapacidades() {
        assertNull(BoardAccessScreenRules.effectiveRoleLabel(WorkspaceRole.UNKNOWN))
        assertNull(BoardAccessScreenRules.effectiveRoleLabel(null))
        assertEquals(emptyList<BoardCapability>(), BoardAccessScreenRules.capabilities(WorkspaceRole.UNKNOWN))
        assertEquals(emptyList<BoardCapability>(), BoardAccessScreenRules.capabilities(null))
    }

    @Test
    fun etiquetaDelRolEfectivo_porRol() {
        assertEquals(RoleLabel.EDITOR, BoardAccessScreenRules.effectiveRoleLabel(WorkspaceRole.EDITOR))
        assertEquals(RoleLabel.OWNER, BoardAccessScreenRules.effectiveRoleLabel(WorkspaceRole.OWNER))
    }

    @Test
    fun panel_soloParaGestoresDeWorkspace() {
        assertTrue(BoardAccessScreenRules.showPanel(WorkspaceRole.OWNER))
        assertTrue(BoardAccessScreenRules.showPanel(WorkspaceRole.ADMIN))
        assertFalse(BoardAccessScreenRules.showPanel(WorkspaceRole.EDITOR))
        assertFalse(BoardAccessScreenRules.showPanel(WorkspaceRole.VIEWER))
        assertFalse(BoardAccessScreenRules.showPanel(WorkspaceRole.UNKNOWN))
    }

    @Test
    fun panel_adminConOverrideViewer_sigueViendoElPanel() {
        val effectiveOnBoard = WorkspaceRole.VIEWER

        assertTrue(BoardAccessScreenRules.showPanel(WorkspaceRole.ADMIN))
        assertEquals(listOf(BoardCapability.VIEW), BoardAccessScreenRules.capabilities(effectiveOnBoard))
    }

    @Test
    fun filas_acciones_gestorSobreNoOwner_fijaYQuita() {
        val row = OverrideRow(member(3, WorkspaceRole.EDITOR), boardOverride(3))

        assertEquals(
            setOf(OverrideAction.SET_ROLE, OverrideAction.REMOVE),
            BoardAccessScreenRules.actions(WorkspaceRole.ADMIN, row, self),
        )
    }

    @Test
    fun filas_acciones_sobreOwnerYYo_ninguna() {
        val owner = OverrideRow(member(1, WorkspaceRole.OWNER), null)
        val me = OverrideRow(self, boardOverride(2))

        assertEquals(emptySet<OverrideAction>(), BoardAccessScreenRules.actions(WorkspaceRole.ADMIN, owner, self))
        assertEquals(emptySet<OverrideAction>(), BoardAccessScreenRules.actions(WorkspaceRole.ADMIN, me, self))
    }

    @Test
    fun filas_acciones_sinOverride_noOfreceQuitar() {
        val row = OverrideRow(member(3, WorkspaceRole.EDITOR), null)

        assertEquals(setOf(OverrideAction.SET_ROLE), BoardAccessScreenRules.actions(WorkspaceRole.OWNER, row, self))
    }

    @Test
    fun filas_acciones_sinGestor_ninguna() {
        val row = OverrideRow(member(3, WorkspaceRole.EDITOR), boardOverride(3))

        assertEquals(emptySet<OverrideAction>(), BoardAccessScreenRules.actions(WorkspaceRole.EDITOR, row, self))
    }

    @Test
    fun filas_rolDelTablero_igualQueElWorkspaceSinOverride() {
        val plain = OverrideRow(member(3, WorkspaceRole.EDITOR), null)
        val custom = OverrideRow(member(3, WorkspaceRole.EDITOR), boardOverride(3, WorkspaceRole.ADMIN))
        val unknown = OverrideRow(member(3, WorkspaceRole.EDITOR), boardOverride(3, WorkspaceRole.UNKNOWN))

        assertEquals(BoardRoleDisplay.SAME_AS_WORKSPACE, BoardAccessScreenRules.boardRoleDisplay(plain))
        assertEquals(BoardRoleDisplay.ADMIN, BoardAccessScreenRules.boardRoleDisplay(custom))
        assertEquals(BoardRoleDisplay.UNKNOWN, BoardAccessScreenRules.boardRoleDisplay(unknown))
    }

    @Test
    fun noOtherMembers_sinFilas() {
        val rows = BoardRoleRules.rows(listOf(member(1, WorkspaceRole.OWNER), self), emptyList(), self)

        assertTrue(rows.isEmpty())
    }

    @Test
    fun toUi_copiaNombreCorreoYRoles() {
        val row = OverrideRow(member(3, WorkspaceRole.EDITOR, name = "Carla", email = "carla@example.com"), null)

        val ui = BoardAccessScreenRules.toUi(WorkspaceRole.ADMIN, row, self)

        assertEquals(uuid(3), ui.userId)
        assertEquals("Carla", ui.title)
        assertEquals("carla@example.com", ui.email)
        assertEquals(RoleLabel.EDITOR, ui.workspaceRole)
    }
}
