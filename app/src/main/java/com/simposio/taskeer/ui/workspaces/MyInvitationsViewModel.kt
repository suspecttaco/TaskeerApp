package com.simposio.taskeer.ui.workspaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.MyInvitationsRepository
import com.simposio.taskeer.data.repository.WorkspaceRepository
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.auth.VerificationInput
import com.simposio.taskeer.domain.workspace.AcceptOutcome
import com.simposio.taskeer.domain.workspace.InvitationRules
import com.simposio.taskeer.domain.workspace.InvitationStatus
import com.simposio.taskeer.domain.workspace.MyInvitationsState
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import java.time.Instant
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MyInvitationsViewModel(
    private val inbox: MyInvitationsRepository,
    private val workspaces: WorkspaceRepository,
    session: StateFlow<SessionState>,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {

    private data class Transient(
        val refreshing: Boolean = false,
        val error: WorkspaceInvitationErrorMessage? = null,
        val accepting: Set<String> = emptySet(),
        val dialog: InboxDialog = InboxDialog.NONE,
        val submitting: Boolean = false,
        val dialogError: WorkspaceInvitationErrorMessage? = null,
    )

    private val transient = MutableStateFlow(Transient())
    private var opened = false

    // Canal y no estado: el aviso ocurre una vez y no debe repetirse al rotar.
    private val eventChannel = Channel<InboxEvent>(Channel.BUFFERED)

    val events: Flow<InboxEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<MyInvitationsUiState> = combine(inbox.state, transient) { state, local ->
        state.toUi(local)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MyInvitationsUiState())

    init {
        viewModelScope.launch {
            session.collect { if (it is SessionState.SignedOut) eventChannel.send(InboxEvent.LeaveScreen) }
        }
    }

    fun onOpened() {
        if (opened) return
        opened = true
        refresh()
    }

    fun refresh() {
        if (transient.value.refreshing) return
        update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            try {
                reload()
            } finally {
                update { it.copy(refreshing = false) }
            }
        }
    }

    private suspend fun reload() {
        val outcome = inbox.refresh()
        if (outcome is WorkspaceOutcome.Failed) reportRefreshFailure(outcome)
    }

    private suspend fun reportRefreshFailure(failure: WorkspaceOutcome.Failed) {
        val message = WorkspaceInvitationErrorRules.messageFor(failure.error, failure.retryAfterSeconds)
        if (inbox.state.value.loaded) {
            message?.let { eventChannel.send(InboxEvent.ShowError(it)) }
        } else {
            update { it.copy(error = message ?: WorkspaceInvitationErrorMessage.Generic) }
        }
    }

    fun accept(invitationId: String) {
        val target = inbox.state.value.items.firstOrNull { it.id == invitationId } ?: return
        if (invitationId in transient.value.accepting || !InvitationRules.canAccept(target, clock())) return
        update { it.copy(accepting = it.accepting + invitationId) }
        viewModelScope.launch {
            try {
                when (val outcome = inbox.accept(invitationId)) {
                    is AcceptOutcome.Accepted -> onAccepted(outcome, rereadInbox = false)
                    is AcceptOutcome.Failed -> onAcceptFailed(outcome, fromInbox = true)
                }
            } finally {
                update { it.copy(accepting = it.accepting - invitationId) }
            }
        }
    }

    fun openAcceptWithLink() {
        if (transient.value.submitting) return
        update { it.copy(dialog = InboxDialog.ACCEPT_WITH_LINK, dialogError = null) }
    }

    fun dismissDialog() {
        if (transient.value.submitting) return
        update { it.copy(dialog = InboxDialog.NONE, dialogError = null) }
    }

    // El texto pegado solo existe como parámetro: ni el estado ni el ViewModel lo guardan.
    fun acceptWithLink(rawText: String) {
        if (transient.value.dialog != InboxDialog.ACCEPT_WITH_LINK || transient.value.submitting) return
        val token = VerificationInput.parseToken(rawText)
        if (token == null) {
            update { it.copy(dialogError = WorkspaceInvitationErrorMessage.InvalidLink) }
            return
        }
        update { it.copy(submitting = true, dialogError = null) }
        viewModelScope.launch {
            try {
                when (val outcome = inbox.acceptWithToken(token)) {
                    is AcceptOutcome.Accepted -> {
                        update { it.copy(dialog = InboxDialog.NONE) }
                        onAccepted(outcome, rereadInbox = true)
                    }
                    is AcceptOutcome.Failed -> onAcceptFailed(outcome, fromInbox = false)
                }
            } finally {
                update { it.copy(submitting = false) }
            }
        }
    }

    private suspend fun onAccepted(accepted: AcceptOutcome.Accepted, rereadInbox: Boolean) {
        workspaces.refresh()
        if (rereadInbox) reload()
        val notice = if (accepted.alreadyMember) {
            InboxNotice.AlreadyMember(accepted.workspaceName)
        } else {
            InboxNotice.Joined(accepted.workspaceName)
        }
        eventChannel.send(InboxEvent.ShowMessage(notice))
    }

    private suspend fun onAcceptFailed(failure: AcceptOutcome.Failed, fromInbox: Boolean) {
        val message = WorkspaceInvitationErrorRules.messageFor(failure.error, failure.retryAfterSeconds) ?: return
        if (fromInbox) {
            eventChannel.send(InboxEvent.ShowError(message))
            if (message == WorkspaceInvitationErrorMessage.InvalidInvitation) reload()
        } else {
            update { it.copy(dialogError = message) }
        }
    }

    private fun MyInvitationsState.toUi(local: Transient): MyInvitationsUiState {
        val now = clock()
        val rows = InvitationRules.visibleForInvitee(items, now).map { invitation ->
            val effective = InvitationRules.effectiveStatus(invitation.status, invitation.expiresAt, now)
            MyInvitationRowUi(
                id = invitation.id,
                workspaceName = invitation.workspaceName,
                role = WorkspaceMembersScreenRules.roleLabel(invitation.role),
                expiresAt = invitation.expiresAt,
                expired = effective != InvitationStatus.PENDING,
                canAccept = InvitationRules.canAccept(invitation, now),
                accepting = invitation.id in local.accepting,
            )
        }
        val phase = when {
            loaded -> if (rows.isEmpty()) InboxPhase.EMPTY else InboxPhase.LIST
            local.refreshing -> InboxPhase.LOADING
            local.error != null -> InboxPhase.ERROR
            else -> InboxPhase.LOADING
        }
        return MyInvitationsUiState(
            phase = phase,
            rows = rows,
            refreshing = local.refreshing && loaded,
            error = local.error,
            dialog = local.dialog,
            submitting = local.submitting,
            dialogError = local.dialogError,
        )
    }

    private fun update(change: (Transient) -> Transient) {
        transient.value = change(transient.value)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
