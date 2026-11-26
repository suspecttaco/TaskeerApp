package com.simposio.taskeer.domain.workspace

import com.simposio.taskeer.domain.model.DomainError
import java.time.Instant

enum class InvitationStatus { PENDING, ACCEPTED, EXPIRED, REVOKED, UNKNOWN }

data class Invitation(
    val id: String,
    val workspaceId: String,
    val email: String,
    val role: WorkspaceRole,
    val status: InvitationStatus,
    val invitedById: String,
    val expiresAt: Instant,
    val createdAt: Instant,
) {
    // El correo del invitado y los ids no deben acabar en logs ni mensajes.
    override fun toString(): String = "Invitation(redactada, role=$role, status=$status)"
}

data class MyInvitation(
    val id: String,
    val workspaceId: String,
    val workspaceName: String,
    val role: WorkspaceRole,
    val status: InvitationStatus,
    val expiresAt: Instant,
    val createdAt: Instant,
) {
    // El nombre del workspace es un dato de usuario: no debe acabar en logs ni mensajes.
    override fun toString(): String = "MyInvitation(redactada, role=$role, status=$status)"
}

data class InvitationsState(
    val workspaceId: String? = null,
    val items: List<Invitation> = emptyList(),
    val loaded: Boolean = false,
)

data class MyInvitationsState(
    val items: List<MyInvitation> = emptyList(),
    val loaded: Boolean = false,
)

sealed interface AcceptOutcome {
    class Accepted(val workspaceName: String, val alreadyMember: Boolean) : AcceptOutcome {
        override fun toString(): String = "Accepted(alreadyMember=$alreadyMember)"
    }

    data class Failed(val error: DomainError, val retryAfterSeconds: Long? = null) : AcceptOutcome
}

data class InvitationConfirmedOps(
    val removedIds: Set<String> = emptySet(),
    val added: List<Invitation> = emptyList(),
)
