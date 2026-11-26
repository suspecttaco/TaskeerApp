package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.MyInvitationsRepository
import com.simposio.taskeer.data.repository.WorkspaceInvitationsRepository
import com.simposio.taskeer.domain.workspace.AcceptOutcome
import com.simposio.taskeer.domain.workspace.Invitation
import com.simposio.taskeer.domain.workspace.InvitationRules
import com.simposio.taskeer.domain.workspace.InvitationStatus
import com.simposio.taskeer.domain.workspace.InvitationsState
import com.simposio.taskeer.domain.workspace.MyInvitation
import com.simposio.taskeer.domain.workspace.MyInvitationsState
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeWorkspaceInvitationsRepository(initial: InvitationsState = InvitationsState()) : WorkspaceInvitationsRepository {
    val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<InvitationsState> = mutableState

    var loadResult: WorkspaceOutcome = WorkspaceOutcome.Done
    var loadItems: List<Invitation>? = null
    var inviteResult: WorkspaceOutcome = WorkspaceOutcome.Done
    var inviteGate: CompletableDeferred<Unit>? = null
    var revokeResult: WorkspaceOutcome = WorkspaceOutcome.Done
    val loads = mutableListOf<String>()
    val invites = mutableListOf<Pair<String, WorkspaceRole>>()
    val revocations = mutableListOf<String>()

    override suspend fun load(workspaceId: String): WorkspaceOutcome {
        loads += workspaceId
        loadItems?.let { mutableState.value = InvitationsState(workspaceId, InvitationRules.sorted(it), loaded = true) }
        return loadResult
    }

    override suspend fun invite(email: String, role: WorkspaceRole): WorkspaceOutcome {
        invites += email to role
        inviteGate?.await()
        return inviteResult
    }

    override suspend fun revoke(invitationId: String): WorkspaceOutcome {
        revocations += invitationId
        if (revokeResult is WorkspaceOutcome.Done) {
            mutableState.value = mutableState.value.copy(items = mutableState.value.items.filter { it.id != invitationId })
        }
        return revokeResult
    }
}

class FakeMyInvitationsRepository(initial: MyInvitationsState = MyInvitationsState()) : MyInvitationsRepository {
    val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<MyInvitationsState> = mutableState

    var refreshResult: WorkspaceOutcome = WorkspaceOutcome.Done
    var refreshItems: List<MyInvitation>? = null
    var refreshGate: CompletableDeferred<Unit>? = null
    var acceptResult: AcceptOutcome = AcceptOutcome.Accepted("Equipo", alreadyMember = false)
    var refreshCalls = 0
    val accepted = mutableListOf<String>()
    val tokens = mutableListOf<String>()

    override suspend fun refresh(): WorkspaceOutcome {
        refreshCalls++
        refreshGate?.await()
        refreshItems?.let { mutableState.value = MyInvitationsState(InvitationRules.sortedMine(it), loaded = true) }
        return refreshResult
    }

    override suspend fun accept(invitationId: String): AcceptOutcome {
        accepted += invitationId
        if (acceptResult is AcceptOutcome.Accepted) {
            mutableState.value = mutableState.value.copy(items = mutableState.value.items.filter { it.id != invitationId })
        }
        return acceptResult
    }

    override suspend fun acceptWithToken(token: String): AcceptOutcome {
        tokens += token
        return acceptResult
    }
}

private val INVITATION_CREATED = Instant.parse("2026-11-20T10:00:00Z")

fun invitation(
    n: Int,
    email: String = "i$n@example.com",
    role: WorkspaceRole = WorkspaceRole.VIEWER,
    status: InvitationStatus = InvitationStatus.PENDING,
    expiresAt: Instant = Instant.parse("2026-12-20T10:00:00Z"),
    createdAt: Instant = INVITATION_CREATED.plusSeconds(n.toLong()),
    workspaceId: String = uuid(100),
): Invitation = Invitation(
    id = uuid(500 + n),
    workspaceId = workspaceId,
    email = email,
    role = role,
    status = status,
    invitedById = uuid(1),
    expiresAt = expiresAt,
    createdAt = createdAt,
)

fun myInvitation(
    n: Int,
    workspaceName: String = "Equipo $n",
    role: WorkspaceRole = WorkspaceRole.EDITOR,
    status: InvitationStatus = InvitationStatus.PENDING,
    expiresAt: Instant = Instant.parse("2026-12-20T10:00:00Z"),
    createdAt: Instant = INVITATION_CREATED.plusSeconds(n.toLong()),
): MyInvitation = MyInvitation(
    id = uuid(600 + n),
    workspaceId = uuid(700 + n),
    workspaceName = workspaceName,
    role = role,
    status = status,
    expiresAt = expiresAt,
    createdAt = createdAt,
)
