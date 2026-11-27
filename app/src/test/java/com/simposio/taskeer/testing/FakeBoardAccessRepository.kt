package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.BoardAccessRepository
import com.simposio.taskeer.domain.workspace.BoardAccessState
import com.simposio.taskeer.domain.workspace.BoardMemberOverride
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeBoardAccessRepository(initial: BoardAccessState = BoardAccessState()) : BoardAccessRepository {
    val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<BoardAccessState> = mutableState

    var readResult: WorkspaceOutcome = WorkspaceOutcome.Done
    var effectiveRoleOnRead: WorkspaceRole = WorkspaceRole.EDITOR
    var boardNameOnRead: String = "Tablero"
    var readGate: CompletableDeferred<Unit>? = null
    var overridesResult: WorkspaceOutcome = WorkspaceOutcome.Done
    var overridesOnLoad: List<BoardMemberOverride>? = null
    var mutationResult: WorkspaceOutcome = WorkspaceOutcome.Done
    var mutationGate: CompletableDeferred<Unit>? = null
    val opens = mutableListOf<Pair<String, String>>()
    var refreshCalls = 0
    var overrideLoads = 0
    val sets = mutableListOf<Pair<String, WorkspaceRole>>()
    val removals = mutableListOf<String>()

    override suspend fun open(workspaceId: String, boardId: String): WorkspaceOutcome {
        opens += workspaceId to boardId
        val current = mutableState.value
        if (current.workspaceId != workspaceId || current.boardId != boardId) {
            mutableState.value = BoardAccessState(workspaceId = workspaceId, boardId = boardId)
        }
        return read()
    }

    override suspend fun refresh(): WorkspaceOutcome {
        refreshCalls++
        return read()
    }

    private suspend fun read(): WorkspaceOutcome {
        readGate?.await()
        if (readResult == WorkspaceOutcome.Done) {
            mutableState.value = mutableState.value.copy(boardName = boardNameOnRead, effectiveRole = effectiveRoleOnRead)
        }
        return readResult
    }

    override suspend fun loadOverrides(): WorkspaceOutcome {
        overrideLoads++
        if (overridesResult == WorkspaceOutcome.Done) {
            overridesOnLoad?.let { mutableState.value = mutableState.value.copy(overrides = it, overridesLoaded = true) }
        }
        return overridesResult
    }

    override suspend fun setOverride(userId: String, role: WorkspaceRole): WorkspaceOutcome {
        sets += userId to role
        mutationGate?.await()
        if (mutationResult == WorkspaceOutcome.Done) {
            val saved = BoardMemberOverride(userId.takeLast(12), userId, role, Instant.parse("2026-11-02T08:00:00Z"))
            val state = mutableState.value
            mutableState.value = state.copy(overrides = state.overrides.filter { it.userId != userId } + saved)
        }
        return mutationResult
    }

    override suspend fun removeOverride(userId: String): WorkspaceOutcome {
        removals += userId
        mutationGate?.await()
        if (mutationResult == WorkspaceOutcome.Done) {
            val state = mutableState.value
            mutableState.value = state.copy(overrides = state.overrides.filter { it.userId != userId })
        }
        return mutationResult
    }
}
