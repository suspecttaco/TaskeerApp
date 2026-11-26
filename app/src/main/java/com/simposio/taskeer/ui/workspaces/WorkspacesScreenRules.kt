package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.workspace.Workspace
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.domain.workspace.WorkspaceRules

enum class RowAction { RENAME, DELETE }

enum class RoleLabel { PERSONAL, OWNER, ADMIN, EDITOR, VIEWER, MEMBER }

object WorkspacesScreenRules {

    fun showEntry(session: SessionState): Boolean = session is SessionState.SignedIn

    fun rowActions(workspace: Workspace): Set<RowAction> = buildSet {
        if (WorkspaceRules.canRename(workspace.myRole)) add(RowAction.RENAME)
        if (WorkspaceRules.canDelete(workspace.myRole, workspace.isPersonal)) add(RowAction.DELETE)
    }

    fun roleLabel(workspace: Workspace): RoleLabel = when {
        workspace.isPersonal -> RoleLabel.PERSONAL
        else -> when (workspace.myRole) {
            WorkspaceRole.OWNER -> RoleLabel.OWNER
            WorkspaceRole.ADMIN -> RoleLabel.ADMIN
            WorkspaceRole.EDITOR -> RoleLabel.EDITOR
            WorkspaceRole.VIEWER -> RoleLabel.VIEWER
            WorkspaceRole.UNKNOWN -> RoleLabel.MEMBER
        }
    }
}
