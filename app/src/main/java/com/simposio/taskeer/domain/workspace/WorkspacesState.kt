package com.simposio.taskeer.domain.workspace

import com.simposio.taskeer.domain.model.DomainError

data class WorkspacesState(
    val items: List<Workspace> = emptyList(),
    val loaded: Boolean = false,
)

sealed interface WorkspaceOutcome {
    data object Done : WorkspaceOutcome

    data class Failed(val error: DomainError, val retryAfterSeconds: Long? = null) : WorkspaceOutcome
}
