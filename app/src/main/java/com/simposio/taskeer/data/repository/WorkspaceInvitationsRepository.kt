package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.workspace.InvitationsState
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import kotlinx.coroutines.flow.StateFlow

interface WorkspaceInvitationsRepository {
    val state: StateFlow<InvitationsState>

    suspend fun load(workspaceId: String): WorkspaceOutcome

    // 201: la invitación se añade a la lista; no es optimista.
    suspend fun invite(email: String, role: WorkspaceRole): WorkspaceOutcome

    // 200 o 404 de la invitación: deja de existir y se quita de la lista.
    suspend fun revoke(invitationId: String): WorkspaceOutcome
}
