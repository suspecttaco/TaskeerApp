package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.workspace.BoardCapability
import com.simposio.taskeer.domain.workspace.BoardRoleRules
import com.simposio.taskeer.domain.workspace.OverrideRow
import com.simposio.taskeer.domain.workspace.WorkspaceMember
import com.simposio.taskeer.domain.workspace.WorkspaceRole

enum class OverrideAction { SET_ROLE, REMOVE }

enum class BoardRoleDisplay { SAME_AS_WORKSPACE, OWNER, ADMIN, EDITOR, VIEWER, UNKNOWN }

object BoardAccessScreenRules {

    // UNKNOWN y un rol aún no leído no se enseñan como rol: la pantalla dice «Rol no disponible».
    fun effectiveRoleLabel(role: WorkspaceRole?): RoleLabel? = when (role) {
        WorkspaceRole.OWNER -> RoleLabel.OWNER
        WorkspaceRole.ADMIN -> RoleLabel.ADMIN
        WorkspaceRole.EDITOR -> RoleLabel.EDITOR
        WorkspaceRole.VIEWER -> RoleLabel.VIEWER
        WorkspaceRole.UNKNOWN, null -> null
    }

    fun capabilities(role: WorkspaceRole?): List<BoardCapability> = BoardRoleRules.capabilities(role)

    fun showPanel(workspaceRole: WorkspaceRole): Boolean = BoardRoleRules.canManageOverrides(workspaceRole)

    fun boardRoleDisplay(row: OverrideRow): BoardRoleDisplay = when (row.override?.role) {
        null -> BoardRoleDisplay.SAME_AS_WORKSPACE
        WorkspaceRole.OWNER -> BoardRoleDisplay.OWNER
        WorkspaceRole.ADMIN -> BoardRoleDisplay.ADMIN
        WorkspaceRole.EDITOR -> BoardRoleDisplay.EDITOR
        WorkspaceRole.VIEWER -> BoardRoleDisplay.VIEWER
        WorkspaceRole.UNKNOWN -> BoardRoleDisplay.UNKNOWN
    }

    fun actions(workspaceRole: WorkspaceRole, row: OverrideRow, self: WorkspaceMember?): Set<OverrideAction> = buildSet {
        if (BoardRoleRules.canSetOverride(workspaceRole, row.member, self)) add(OverrideAction.SET_ROLE)
        if (BoardRoleRules.canRemoveOverride(workspaceRole, row.member, self, row.override)) add(OverrideAction.REMOVE)
    }

    fun toUi(workspaceRole: WorkspaceRole, row: OverrideRow, self: WorkspaceMember?): OverrideRowUi = OverrideRowUi(
        userId = row.member.userId,
        title = row.member.displayName,
        email = row.member.email,
        workspaceRole = WorkspaceMembersScreenRules.roleLabel(row.member.role),
        boardRole = boardRoleDisplay(row),
        actions = actions(workspaceRole, row, self),
    )
}
