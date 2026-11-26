package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.workspace.Invitation
import com.simposio.taskeer.domain.workspace.InvitationRules
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import java.time.Instant

enum class InvitationAction { REVOKE }

object WorkspaceInvitationsScreenRules {

    fun sectionVisible(actor: WorkspaceRole, isPersonal: Boolean): Boolean = InvitationRules.canInvite(actor, isPersonal)

    fun actions(actor: WorkspaceRole, invitation: Invitation, now: Instant): Set<InvitationAction> = buildSet {
        if (InvitationRules.canRevoke(actor, invitation, now)) add(InvitationAction.REVOKE)
    }
}
