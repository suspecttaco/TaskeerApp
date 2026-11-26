package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.workspace.MembersState
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import kotlinx.coroutines.flow.StateFlow

interface WorkspaceMembersRepository {
    val state: StateFlow<MembersState>

    suspend fun load(workspaceId: String): WorkspaceOutcome

    suspend fun changeRole(userId: String, role: WorkspaceRole): WorkspaceOutcome

    // Expulsar o salir: el servidor trata igual el DELETE de uno mismo.
    suspend fun remove(userId: String): WorkspaceOutcome

    suspend fun transferOwnership(newOwnerUserId: String): WorkspaceOutcome
}
