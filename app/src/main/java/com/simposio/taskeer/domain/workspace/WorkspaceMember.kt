package com.simposio.taskeer.domain.workspace

import java.time.Instant

data class WorkspaceMember(
    val userId: String,
    val email: String,
    val displayName: String,
    val role: WorkspaceRole,
    val createdAt: Instant,
) {
    // Correo y nombre son datos personales de terceros: no deben acabar en logs ni mensajes.
    override fun toString(): String = "WorkspaceMember(userId redactado, role=$role)"
}

data class MembersState(
    val workspaceId: String? = null,
    val items: List<WorkspaceMember> = emptyList(),
    val loaded: Boolean = false,
)
