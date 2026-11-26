package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.workspace.WorkspaceRole
import org.junit.Assert.assertFalse
import org.junit.Test

class WorkspaceMembersUiStateTest {

    @Test
    fun memberRowUi_toString_noIncluyeDatosPersonales() {
        val row = MemberRowUi("uid-secreto", "Nombre Secreto", "secreto@example.com", RoleLabel.EDITOR, isSelf = false, actions = emptySet())

        val text = row.toString()

        assertFalse(text.contains("Secreto") || text.contains("secreto@") || text.contains("uid-secreto"))
    }

    @Test
    fun memberDialog_toString_noIncluyeDatosPersonales() {
        val dialogs = listOf(
            MemberDialog.ChangeRole("uid-secreto", "Nombre Secreto", listOf(WorkspaceRole.ADMIN)),
            MemberDialog.ConfirmRemove("uid-secreto", "Nombre Secreto"),
            MemberDialog.ConfirmTransfer("uid-secreto", "Nombre Secreto"),
        )

        dialogs.forEach {
            val text = it.toString()
            assertFalse(text.contains("Secreto") || text.contains("uid-secreto"))
        }
    }
}
