package com.simposio.taskeer.ui.workspaces

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.WorkspaceInvitationsRepository
import com.simposio.taskeer.data.repository.WorkspaceMembersRepository
import com.simposio.taskeer.data.repository.WorkspaceRepository
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.workspace.Invitation
import com.simposio.taskeer.domain.workspace.InvitationRules
import com.simposio.taskeer.domain.workspace.InvitationStatus
import com.simposio.taskeer.domain.workspace.InvitationsState
import com.simposio.taskeer.domain.workspace.InviteInput
import com.simposio.taskeer.domain.workspace.InviterVerification
import com.simposio.taskeer.domain.workspace.MemberRules
import com.simposio.taskeer.domain.workspace.MembersState
import com.simposio.taskeer.domain.workspace.WorkspaceMember
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.domain.workspace.WorkspacesState
import java.time.Instant
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkspaceInvitationsViewModel(
    savedStateHandle: SavedStateHandle,
    private val invitations: WorkspaceInvitationsRepository,
    private val members: WorkspaceMembersRepository,
    private val workspaces: WorkspaceRepository,
    private val session: StateFlow<SessionState>,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {

    private val workspaceId: String = requireNotNull(savedStateHandle[WorkspaceMembersViewModel.WORKSPACE_ID_ARG]) {
        "Falta el workspaceId de la ruta"
    }

    private data class Transient(
        val error: WorkspaceInvitationErrorMessage? = null,
        val forbidden: Boolean = false,
        val dialog: InvitationDialog = InvitationDialog.None,
        val submitting: Boolean = false,
        val dialogError: WorkspaceInvitationErrorMessage? = null,
    )

    // Foto coherente de lo que decide qué acciones se ofrecen.
    private class Context(
        val actor: WorkspaceRole,
        val isPersonal: Boolean,
        val membersLoaded: Boolean,
        val memberItems: List<WorkspaceMember>,
    ) {
        val canInvite: Boolean get() = membersLoaded && InvitationRules.canInvite(actor, isPersonal)
    }

    private val transient = MutableStateFlow(Transient())

    // Canal y no estado: el aviso ocurre una vez y no debe repetirse al rotar.
    private val eventChannel = Channel<InvitationEvent>(Channel.BUFFERED)

    val events: Flow<InvitationEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<WorkspaceInvitationsUiState> = combine(
        invitations.state,
        members.state,
        workspaces.state,
        session,
        transient,
    ) { invitationState, memberState, workspaceState, sessionState, local ->
        toUi(invitationState, contextOf(memberState, workspaceState, sessionState), local)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WorkspaceInvitationsUiState())

    init {
        viewModelScope.launch {
            session.collect { if (it is SessionState.SignedOut) eventChannel.send(InvitationEvent.LeaveScreen) }
        }
        // No se pide la lista a quien no es gestor: el servidor respondería 403.
        viewModelScope.launch {
            combine(members.state, workspaces.state, session) { memberState, workspaceState, sessionState ->
                contextOf(memberState, workspaceState, sessionState).canInvite
            }.distinctUntilChanged().filter { it }.collect { loadInvitations() }
        }
    }

    fun refresh() {
        if (!currentContext().canInvite) return
        update { it.copy(error = null, forbidden = false) }
        viewModelScope.launch { loadInvitations() }
    }

    private suspend fun loadInvitations() {
        val outcome = invitations.load(workspaceId)
        if (outcome is WorkspaceOutcome.Failed) reportLoadFailure(outcome)
    }

    private suspend fun reportLoadFailure(failure: WorkspaceOutcome.Failed) {
        val message = WorkspaceInvitationErrorRules.messageFor(failure.error, failure.retryAfterSeconds)
        val loaded = invitations.state.value.let { it.loaded && it.workspaceId == workspaceId }
        when {
            message == WorkspaceInvitationErrorMessage.NoPermission -> {
                update { it.copy(forbidden = true) }
                eventChannel.send(InvitationEvent.ShowError(message))
                reloadAll()
            }
            message == WorkspaceInvitationErrorMessage.WorkspaceGone -> leaveBecauseGone()
            loaded -> message?.let { eventChannel.send(InvitationEvent.ShowError(it)) }
            else -> update { it.copy(error = message ?: WorkspaceInvitationErrorMessage.Generic) }
        }
    }

    fun openInvite() {
        val context = currentContext()
        if (transient.value.submitting || !context.canInvite) return
        val dialog = when (InvitationRules.verification(verifiedEmailFlag())) {
            InviterVerification.Unverified -> InvitationDialog.VerifyEmail()
            InviterVerification.Verified, InviterVerification.Unknown -> InvitationDialog.Invite()
        }
        showDialog(dialog)
    }

    fun invite(rawEmail: String, role: WorkspaceRole) {
        val dialog = transient.value.dialog as? InvitationDialog.Invite ?: return
        if (transient.value.submitting) return
        val context = currentContext()
        if (!context.canInvite || role !in InvitationRules.INVITABLE) return
        val draft = InvitationDialog.Invite(rawEmail, role)
        if (InvitationRules.verification(verifiedEmailFlag()) == InviterVerification.Unverified) {
            showDialog(InvitationDialog.VerifyEmail(rawEmail, role))
            return
        }
        val email = when (
            val check = InvitationRules.validateEmail(rawEmail, context.memberItems, invitations.state.value.items, clock())
        ) {
            is InviteInput.Valid -> check.email
            InviteInput.Blank, InviteInput.Malformed -> return rejectLocally(draft, WorkspaceInvitationErrorMessage.InvalidEmail)
            InviteInput.AlreadyMember -> return rejectLocally(draft, WorkspaceInvitationErrorMessage.AlreadyMember)
            InviteInput.AlreadyInvited -> return rejectLocally(draft, WorkspaceInvitationErrorMessage.AlreadyInvited)
        }
        update { it.copy(dialog = draft, submitting = true, dialogError = null) }
        viewModelScope.launch {
            try {
                when (val outcome = invitations.invite(email, role)) {
                    WorkspaceOutcome.Done -> {
                        showDialog(InvitationDialog.None)
                        eventChannel.send(InvitationEvent.ShowMessage(InvitationNotice.Invited(email)))
                    }
                    is WorkspaceOutcome.Failed -> onInviteFailure(outcome, draft)
                }
            } finally {
                update { it.copy(submitting = false) }
            }
        }
    }

    private fun rejectLocally(draft: InvitationDialog.Invite, message: WorkspaceInvitationErrorMessage) {
        update { it.copy(dialog = draft, dialogError = message) }
    }

    private suspend fun onInviteFailure(failure: WorkspaceOutcome.Failed, draft: InvitationDialog.Invite) {
        when (val message = WorkspaceInvitationErrorRules.messageFor(failure.error, failure.retryAfterSeconds)) {
            null -> showDialog(InvitationDialog.None)
            WorkspaceInvitationErrorMessage.EmailNotVerified -> showDialog(InvitationDialog.VerifyEmail(draft.email, draft.role))
            WorkspaceInvitationErrorMessage.NoPermission -> {
                showDialog(InvitationDialog.None)
                eventChannel.send(InvitationEvent.ShowError(message))
                reloadAll()
            }
            WorkspaceInvitationErrorMessage.WorkspaceGone -> {
                showDialog(InvitationDialog.None)
                leaveBecauseGone()
            }
            else -> update { it.copy(dialogError = message) }
        }
    }

    fun requestRevoke(invitationId: String) {
        if (transient.value.submitting) return
        val target = invitations.state.value.items.firstOrNull { it.id == invitationId } ?: return
        if (!InvitationRules.canRevoke(currentContext().actor, target, clock())) return
        showDialog(InvitationDialog.ConfirmRevoke(invitationId, target.email))
    }

    fun confirmRevoke() {
        val dialog = transient.value.dialog as? InvitationDialog.ConfirmRevoke ?: return
        if (transient.value.submitting) return
        val target = invitations.state.value.items.firstOrNull { it.id == dialog.invitationId } ?: return
        if (!InvitationRules.canRevoke(currentContext().actor, target, clock())) return
        update { it.copy(submitting = true) }
        viewModelScope.launch {
            try {
                when (val outcome = invitations.revoke(dialog.invitationId)) {
                    WorkspaceOutcome.Done -> showDialog(InvitationDialog.None)
                    is WorkspaceOutcome.Failed -> onRevokeFailure(outcome)
                }
            } finally {
                update { it.copy(submitting = false) }
            }
        }
    }

    private suspend fun onRevokeFailure(failure: WorkspaceOutcome.Failed) {
        val message = WorkspaceInvitationErrorRules.messageFor(failure.error, failure.retryAfterSeconds)
        showDialog(InvitationDialog.None)
        when (message) {
            null -> Unit
            WorkspaceInvitationErrorMessage.WorkspaceGone -> leaveBecauseGone()
            WorkspaceInvitationErrorMessage.NoPermission -> {
                eventChannel.send(InvitationEvent.ShowError(message))
                reloadAll()
            }
            else -> eventChannel.send(InvitationEvent.ShowError(message))
        }
    }

    fun verifyEmail() {
        if (transient.value.dialog !is InvitationDialog.VerifyEmail) return
        showDialog(InvitationDialog.None)
        eventChannel.trySend(InvitationEvent.OpenVerifyEmail)
    }

    fun dismissDialog() {
        if (transient.value.submitting) return
        showDialog(InvitationDialog.None)
    }

    private suspend fun leaveBecauseGone() {
        workspaces.refresh()
        eventChannel.send(InvitationEvent.ShowError(WorkspaceInvitationErrorMessage.WorkspaceGone))
        eventChannel.send(InvitationEvent.LeaveScreen)
    }

    // Rol obsoleto: lo que la pantalla ofrece no coincide con el servidor.
    private suspend fun reloadAll() {
        members.load(workspaceId)
        workspaces.refresh()
    }

    private fun verifiedEmailFlag(): Boolean? = (session.value as? SessionState.SignedIn)?.account?.emailVerified

    private fun currentContext(): Context = contextOf(members.state.value, workspaces.state.value, session.value)

    private fun contextOf(memberState: MembersState, workspaceState: WorkspacesState, sessionState: SessionState): Context {
        val workspace = workspaceState.items.firstOrNull { it.id == workspaceId }
        val current = memberState.workspaceId == workspaceId
        val items = if (current) memberState.items else emptyList()
        val email = (sessionState as? SessionState.SignedIn)?.account?.email
        val actor = workspace?.let { MemberRules.effectiveRole(it, items, email) }
            ?: MemberRules.selfOf(items, email)?.role
            ?: WorkspaceRole.UNKNOWN
        return Context(actor, workspace?.isPersonal == true, current && memberState.loaded, items)
    }

    private fun toUi(state: InvitationsState, context: Context, local: Transient): WorkspaceInvitationsUiState {
        val visible = context.canInvite && !local.forbidden
        if (!visible) return WorkspaceInvitationsUiState(dialog = local.dialog, submitting = local.submitting)
        val loaded = state.loaded && state.workspaceId == workspaceId
        val now = clock()
        val rows = if (loaded) visibleRows(state.items, context.actor, now) else emptyList()
        val section = when {
            loaded -> if (rows.isEmpty()) SectionVisibility.EMPTY else SectionVisibility.LIST
            local.error != null -> SectionVisibility.ERROR
            else -> SectionVisibility.LOADING
        }
        return WorkspaceInvitationsUiState(
            section = section,
            rows = rows,
            canInvite = true,
            error = local.error,
            dialog = local.dialog,
            submitting = local.submitting,
            dialogError = local.dialogError,
        )
    }

    private fun visibleRows(items: List<Invitation>, actor: WorkspaceRole, now: Instant): List<InvitationRowUi> =
        InvitationRules.visibleForManagers(items, now).map { invitation ->
            InvitationRowUi(
                id = invitation.id,
                email = invitation.email,
                role = WorkspaceMembersScreenRules.roleLabel(invitation.role),
                expiresAt = invitation.expiresAt,
                expired = InvitationRules.effectiveStatus(invitation.status, invitation.expiresAt, now) != InvitationStatus.PENDING,
                actions = WorkspaceInvitationsScreenRules.actions(actor, invitation, now),
            )
        }

    private fun showDialog(dialog: InvitationDialog) {
        update { it.copy(dialog = dialog, dialogError = null) }
    }

    private fun update(change: (Transient) -> Transient) {
        transient.value = change(transient.value)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
