package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardPermissionGateTest {

    private fun gateWith(role: WorkspaceRole?) = BoardPermissionGate(
        object : BoardRoles {
            override suspend fun roleOf(boardId: String): WorkspaceRole? = role

            override fun observeRole(boardId: String): Flow<WorkspaceRole?> = flowOf(role)
        },
    )

    @Test
    fun viewer_noCreaTarea() = runTest {
        val denied = gateWith(WorkspaceRole.VIEWER).denyTaskChange("b1")

        val error = denied?.error as DomainError.Forbidden
        assertEquals("BOARD_ROLE_FORBIDS", error.code)
    }

    @Test
    fun viewer_noMueve() = runTest {
        val gate = gateWith(WorkspaceRole.VIEWER)

        assertTrue(gate.denyTaskChange("b1") != null)
        assertTrue(gate.denyColumnChange("b1") != null)
    }

    @Test
    fun editor_gestionaTareasNoColumnas() = runTest {
        val gate = gateWith(WorkspaceRole.EDITOR)

        assertNull(gate.denyTaskChange("b1"))
        assertEquals("BOARD_ROLE_FORBIDS", (gate.denyColumnChange("b1")?.error as DomainError.Forbidden).code)
    }

    @Test
    fun adminOwner_todo() = runTest {
        listOf(WorkspaceRole.ADMIN, WorkspaceRole.OWNER).forEach { role ->
            assertNull(gateWith(role).denyTaskChange("b1"))
            assertNull(gateWith(role).denyColumnChange("b1"))
        }
    }

    @Test
    fun sinRol_tableroLocal_todo() = runTest {
        val gate = gateWith(null)

        assertNull(gate.denyTaskChange("b1"))
        assertNull(gate.denyColumnChange("b1"))
        assertNull(gate.denyTaskChange(null))
    }

    @Test
    fun rolDesconocido_nada() = runTest {
        val gate = gateWith(WorkspaceRole.UNKNOWN)

        assertTrue(gate.denyTaskChange("b1") != null)
        assertTrue(gate.denyColumnChange("b1") != null)
    }
}
