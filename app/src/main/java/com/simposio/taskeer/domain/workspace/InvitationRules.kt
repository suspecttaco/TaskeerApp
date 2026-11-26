package com.simposio.taskeer.domain.workspace

import com.simposio.taskeer.domain.auth.AuthRules
import java.time.Instant

sealed interface InviteInput {
    data class Valid(val email: String) : InviteInput {
        override fun toString(): String = "Valid(redactado)"
    }

    data object Blank : InviteInput

    data object Malformed : InviteInput

    data object AlreadyMember : InviteInput

    data object AlreadyInvited : InviteInput
}

sealed interface InviterVerification {
    data object Verified : InviterVerification

    data object Unknown : InviterVerification

    data object Unverified : InviterVerification
}

object InvitationRules {
    val INVITABLE: List<WorkspaceRole> = MemberRules.ASSIGNABLE
    val DEFAULT_ROLE: WorkspaceRole = WorkspaceRole.VIEWER

    // El servidor puede tardar en marcar EXPIRED: aquí solo se decide qué controles se muestran.
    fun effectiveStatus(status: InvitationStatus, expiresAt: Instant, now: Instant): InvitationStatus =
        if (status == InvitationStatus.PENDING && !expiresAt.isAfter(now)) InvitationStatus.EXPIRED else status

    fun visibleForManagers(items: List<Invitation>, now: Instant): List<Invitation> =
        items
            .filter { effectiveStatus(it.status, it.expiresAt, now) in MANAGER_VISIBLE }
            .sortedWith(
                compareBy<Invitation> { effectiveStatus(it.status, it.expiresAt, now) != InvitationStatus.PENDING }
                    .thenByDescending { it.createdAt }
                    .thenBy { it.id },
            )

    fun visibleForInvitee(items: List<MyInvitation>, now: Instant): List<MyInvitation> =
        items
            .filter { it.status == InvitationStatus.PENDING }
            .sortedWith(compareByDescending<MyInvitation> { it.createdAt }.thenBy { it.id })

    fun canInvite(actor: WorkspaceRole, isPersonal: Boolean): Boolean = MemberRules.canManageMembers(actor) && !isPersonal

    fun canRevoke(actor: WorkspaceRole, invitation: Invitation, now: Instant): Boolean =
        MemberRules.canManageMembers(actor) &&
            invitation.role in INVITABLE &&
            effectiveStatus(invitation.status, invitation.expiresAt, now) == InvitationStatus.PENDING

    fun canAccept(invitation: MyInvitation, now: Instant): Boolean =
        invitation.role in INVITABLE && effectiveStatus(invitation.status, invitation.expiresAt, now) == InvitationStatus.PENDING

    fun verification(emailVerified: Boolean?): InviterVerification = when (emailVerified) {
        true -> InviterVerification.Verified
        false -> InviterVerification.Unverified
        null -> InviterVerification.Unknown
    }

    fun validateEmail(raw: String, members: List<WorkspaceMember>, invitations: List<Invitation>, now: Instant): InviteInput {
        val email = raw.trim()
        return when {
            email.isEmpty() -> InviteInput.Blank
            email.length > AuthRules.EMAIL_MAX || !AuthRules.isEmailShapeValid(email) -> InviteInput.Malformed
            members.any { it.email.trim().equals(email, ignoreCase = true) } -> InviteInput.AlreadyMember
            invitations.any {
                it.email.trim().equals(email, ignoreCase = true) &&
                    effectiveStatus(it.status, it.expiresAt, now) == InvitationStatus.PENDING
            } -> InviteInput.AlreadyInvited
            else -> InviteInput.Valid(email)
        }
    }

    fun sorted(items: List<Invitation>): List<Invitation> =
        items.sortedWith(compareByDescending<Invitation> { it.createdAt }.thenBy { it.id })

    fun sortedMine(items: List<MyInvitation>): List<MyInvitation> =
        items.sortedWith(compareByDescending<MyInvitation> { it.createdAt }.thenBy { it.id })

    // Un refresco que empezó antes de una mutación confirmada trae datos previos a ella.
    fun reapply(fresh: List<Invitation>, confirmed: InvitationConfirmedOps): List<Invitation> =
        sorted(
            (fresh + confirmed.added.filter { added -> fresh.none { it.id == added.id } })
                .filter { it.id !in confirmed.removedIds },
        )

    fun reapplyMine(fresh: List<MyInvitation>, removedIds: Set<String>): List<MyInvitation> =
        sortedMine(fresh.filter { it.id !in removedIds })

    private val MANAGER_VISIBLE = setOf(InvitationStatus.PENDING, InvitationStatus.EXPIRED)
}
