package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.domain.workspace.WorkspacesState
import kotlinx.coroutines.flow.StateFlow

interface WorkspaceRepository {
    val state: StateFlow<WorkspacesState>

    suspend fun refresh(): WorkspaceOutcome

    suspend fun create(name: String): WorkspaceOutcome

    suspend fun rename(id: String, name: String): WorkspaceOutcome

    suspend fun delete(id: String): WorkspaceOutcome
}
