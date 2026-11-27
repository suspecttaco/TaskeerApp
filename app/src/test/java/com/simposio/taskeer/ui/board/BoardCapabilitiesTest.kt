package com.simposio.taskeer.ui.board

import com.simposio.taskeer.domain.workspace.WorkspaceRole
import org.junit.Assert.assertEquals
import org.junit.Test

class BoardCapabilitiesTest {

    @Test
    fun sinRol_ofreceTodo() {
        assertEquals(BoardCapabilities(canEditTasks = true, canManageColumns = true), BoardCapabilities.forRole(null))
    }

    @Test
    fun viewer_noOfreceNada() {
        assertEquals(BoardCapabilities(canEditTasks = false, canManageColumns = false), BoardCapabilities.forRole(WorkspaceRole.VIEWER))
    }

    @Test
    fun editor_soloTareas() {
        assertEquals(BoardCapabilities(canEditTasks = true, canManageColumns = false), BoardCapabilities.forRole(WorkspaceRole.EDITOR))
    }

    @Test
    fun adminYOwner_todo() {
        assertEquals(BoardCapabilities(true, true), BoardCapabilities.forRole(WorkspaceRole.ADMIN))
        assertEquals(BoardCapabilities(true, true), BoardCapabilities.forRole(WorkspaceRole.OWNER))
    }

    @Test
    fun rolDesconocido_noOfreceNada() {
        assertEquals(BoardCapabilities(false, false), BoardCapabilities.forRole(WorkspaceRole.UNKNOWN))
    }
}
