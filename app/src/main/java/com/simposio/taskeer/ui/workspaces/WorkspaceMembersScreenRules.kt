package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.workspace.MemberRules
import com.simposio.taskeer.domain.workspace.WorkspaceMember
import com.simposio.taskeer.domain.workspace.WorkspaceRole

enum class MemberAction { CHANGE_ROLE, REMOVE, LEAVE, TRANSFER }

object WorkspaceMembersScreenRules {

    fun actions(actor: WorkspaceRole, target: WorkspaceMember, self: WorkspaceMember?, isPersonal: Boolean): Set<MemberAction> =
        buildSet {
            if (MemberRules.canChangeRole(actor, target, self)) add(MemberAction.CHANGE_ROLE)
            if (MemberRules.canRemove(actor, target, self)) add(MemberAction.REMOVE)
            if (MemberRules.canTransferOwnership(actor, target, self, isPersonal)) add(MemberAction.TRANSFER)
            if (self?.userId == target.userId && MemberRules.canLeave(actor, isPersonal, self)) add(MemberAction.LEAVE)
        }

    fun ownerHint(actor: WorkspaceRole, isPersonal: Boolean): Boolean = actor == WorkspaceRole.OWNER && !isPersonal

    fun roleLabel(role: WorkspaceRole): RoleLabel = when (role) {
        WorkspaceRole.OWNER -> RoleLabel.OWNER
        WorkspaceRole.ADMIN -> RoleLabel.ADMIN
        WorkspaceRole.EDITOR -> RoleLabel.EDITOR
        WorkspaceRole.VIEWER -> RoleLabel.VIEWER
        WorkspaceRole.UNKNOWN -> RoleLabel.MEMBER
    }
}
