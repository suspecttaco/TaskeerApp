package com.simposio.taskeer.ui.workspaces

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.WorkspaceMembersRepository
import com.simposio.taskeer.data.repository.WorkspaceRepository
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.workspace.MemberRules
import com.simposio.taskeer.domain.workspace.MembersState
import com.simposio.taskeer.domain.workspace.Workspace
import com.simposio.taskeer.domain.workspace.WorkspaceMember
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.domain.workspace.WorkspaceRules
import com.simposio.taskeer.domain.workspace.WorkspacesState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkspaceMembersViewModel(
    savedStateHandle: SavedStateHandle,
    private val members: WorkspaceMembersRepository,
    private val workspaces: WorkspaceRepository,
    private val session: StateFlow<SessionState>,
) : ViewModel() {

    private val workspaceId: String = requireNotNull(savedStateHandle[WORKSPACE_ID_ARG]) { "Falta el workspaceId de la ruta" }

    private data class Transient(
        val refreshing: Boolean = false,
        val error: WorkspaceMemberErrorMessage? = null,
        val dialog: MemberDialog = MemberDialog.None,
        val submitting: Boolean = false,
    )

    // Foto coherente de lo que decide qué acciones se ofrecen.
    private class Context(
        val workspace: Workspace?,
        val items: List<WorkspaceMember>,
        val self: WorkspaceMember?,
        val actor: WorkspaceRole,
    ) {
        val isPersonal: Boolean get() = workspace?.isPersonal == true

        fun member(userId: String): WorkspaceMember? = items.firstOrNull { it.userId == userId }
    }

    private val transient = MutableStateFlow(Transient())
    private var opened = false

    // Canal y no estado: el aviso ocurre una vez y no debe repetirse al rotar.
    private val eventChannel = Channel<MembersEvent>(Channel.BUFFERED)

    val events: Flow<MembersEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<WorkspaceMembersUiState> = combine(
        members.state,
        workspaces.state,
        session,
        transient,
    ) { memberState, workspaceState, sessionState, local ->
        toUi(contextOf(memberState, workspaceState, sessionState), memberState, local)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WorkspaceMembersUiState())

    init {
        viewModelScope.launch {
            session.collect { if (it is SessionState.SignedOut) eventChannel.send(MembersEvent.LeaveScreen) }
        }
    }

    fun onOpened() {
        if (opened) return
        opened = true
        if (!WorkspaceRules.isValidId(workspaceId)) {
            viewModelScope.launch { leaveBecauseGone(refreshWorkspaces = false) }
            return
        }
        update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            try {
                if (workspaces.state.value.items.none { it.id == workspaceId }) {
                    workspaces.refresh()
                    if (workspaces.state.value.items.none { it.id == workspaceId }) {
                        leaveBecauseGone(refreshWorkspaces = false)
                        return@launch
                    }
                }
                loadMembers()
            } finally {
                update { it.copy(refreshing = false) }
            }
        }
    }

    fun refresh() {
        if (transient.value.refreshing) return
        update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            try {
                loadMembers()
            } finally {
                update { it.copy(refreshing = false) }
            }
        }
    }

    private suspend fun loadMembers() {
        val outcome = members.load(workspaceId)
        if (outcome is WorkspaceOutcome.Failed) reportLoadFailure(outcome)
    }

    private suspend fun reportLoadFailure(failure: WorkspaceOutcome.Failed) {
        val message = WorkspaceMemberErrorRules.messageFor(failure.error, failure.retryAfterSeconds)
        if (message == WorkspaceMemberErrorMessage.WorkspaceGone) {
            leaveBecauseGone()
        } else if (members.state.value.loaded && members.state.value.workspaceId == workspaceId) {
            message?.let { eventChannel.send(MembersEvent.ShowError(it)) }
        } else {
            update { it.copy(error = message ?: WorkspaceMemberErrorMessage.Generic) }
        }
    }

    private suspend fun leaveBecauseGone(refreshWorkspaces: Boolean = true) {
        if (refreshWorkspaces) workspaces.refresh()
        eventChannel.send(MembersEvent.ShowError(WorkspaceMemberErrorMessage.WorkspaceGone))
        eventChannel.send(MembersEvent.LeaveScreen)
    }

    fun openBoards() {
        viewModelScope.launch { eventChannel.send(MembersEvent.OpenBoards(workspaceId)) }
    }

    fun openChangeRole(userId: String) {
        val context = currentContext()
        val target = context.member(userId) ?: return
        if (transient.value.submitting || !MemberRules.canChangeRole(context.actor, target, context.self)) return
        showDialog(MemberDialog.ChangeRole(userId, target.displayName, MemberRules.assignableRoles(target)))
    }

    fun requestRemove(userId: String) {
        val context = currentContext()
        val target = context.member(userId) ?: return
        if (transient.value.submitting || !MemberRules.canRemove(context.actor, target, context.self)) return
        showDialog(MemberDialog.ConfirmRemove(userId, target.displayName))
    }

    fun requestLeave() {
        val context = currentContext()
        if (transient.value.submitting || !MemberRules.canLeave(context.actor, context.isPersonal, context.self)) return
        showDialog(MemberDialog.ConfirmLeave)
    }

    fun requestTransfer(userId: String) {
        val context = currentContext()
        val target = context.member(userId) ?: return
        if (transient.value.submitting ||
            !MemberRules.canTransferOwnership(context.actor, target, context.self, context.isPersonal)
        ) {
            return
        }
        showDialog(MemberDialog.ConfirmTransfer(userId, target.displayName))
    }

    fun dismissDialog() {
        if (transient.value.submitting) return
        showDialog(MemberDialog.None)
    }

    fun changeRole(role: WorkspaceRole) {
        val dialog = transient.value.dialog as? MemberDialog.ChangeRole ?: return
        if (transient.value.submitting) return
        val context = currentContext()
        val target = context.member(dialog.userId) ?: return
        if (!MemberRules.canChangeRole(context.actor, target, context.self) || role !in MemberRules.assignableRoles(target)) return
        submit(Operation.CHANGE_ROLE, context) { members.changeRole(dialog.userId, role) }
    }

    fun confirm() {
        if (transient.value.submitting) return
        val context = currentContext()
        when (val dialog = transient.value.dialog) {
            is MemberDialog.ConfirmRemove -> {
                val target = context.member(dialog.userId) ?: return
                if (!MemberRules.canRemove(context.actor, target, context.self)) return
                submit(Operation.REMOVE, context) { members.remove(dialog.userId) }
            }
            MemberDialog.ConfirmLeave -> {
                val self = context.self ?: return
                if (!MemberRules.canLeave(context.actor, context.isPersonal, self)) return
                submit(Operation.LEAVE, context) { members.remove(self.userId) }
            }
            is MemberDialog.ConfirmTransfer -> {
                val target = context.member(dialog.userId) ?: return
                if (!MemberRules.canTransferOwnership(context.actor, target, context.self, context.isPersonal)) return
                submit(Operation.TRANSFER, context, target.displayName) { members.transferOwnership(dialog.userId) }
            }
            is MemberDialog.ChangeRole, MemberDialog.None -> Unit
        }
    }

    private enum class Operation { CHANGE_ROLE, REMOVE, LEAVE, TRANSFER }

    private fun submit(
        operation: Operation,
        context: Context,
        targetName: String = "",
        call: suspend () -> WorkspaceOutcome,
    ) {
        update { it.copy(submitting = true) }
        val workspaceName = context.workspace?.name.orEmpty()
        viewModelScope.launch {
            try {
                when (val outcome = call()) {
                    WorkspaceOutcome.Done -> onSuccess(operation, workspaceName, targetName)
                    is WorkspaceOutcome.Failed -> onFailure(operation, outcome, workspaceName)
                }
            } finally {
                update { it.copy(submitting = false) }
            }
        }
    }

    private suspend fun onSuccess(operation: Operation, workspaceName: String, targetName: String) {
        when (operation) {
            Operation.CHANGE_ROLE, Operation.REMOVE -> showDialog(MemberDialog.None)
            Operation.LEAVE -> leftWorkspace(workspaceName)
            Operation.TRANSFER -> {
                showDialog(MemberDialog.None)
                eventChannel.send(MembersEvent.ShowMessage(MembersNotice.OwnershipTransferred(targetName)))
                reloadAll()
            }
        }
    }

    private suspend fun leftWorkspace(workspaceName: String) {
        showDialog(MemberDialog.None)
        workspaces.refresh()
        eventChannel.send(MembersEvent.ShowMessage(MembersNotice.Left(workspaceName)))
        eventChannel.send(MembersEvent.LeaveScreen)
    }

    private suspend fun onFailure(operation: Operation, failure: WorkspaceOutcome.Failed, workspaceName: String) {
        val message = WorkspaceMemberErrorRules.messageFor(failure.error, failure.retryAfterSeconds)
        showDialog(MemberDialog.None)
        when {
            message == null -> Unit
            message == WorkspaceMemberErrorMessage.WorkspaceGone ->
                if (operation == Operation.LEAVE) leftWorkspace(workspaceName) else leaveBecauseGone()
            else -> {
                eventChannel.send(MembersEvent.ShowError(message))
                if (shouldReload(failure.error)) reloadAll()
            }
        }
    }

    // Rol obsoleto o miembro ya inexistente: lo que la pantalla ofrece no coincide con el servidor.
    private fun shouldReload(error: DomainError): Boolean = when (error) {
        is DomainError.Forbidden -> true
        is DomainError.Validation -> error.code in RELOAD_CODES
        else -> false
    }

    private suspend fun reloadAll() {
        members.load(workspaceId)
        workspaces.refresh()
    }

    private fun currentContext(): Context = contextOf(members.state.value, workspaces.state.value, session.value)

    private fun contextOf(memberState: MembersState, workspaceState: WorkspacesState, sessionState: SessionState): Context {
        val workspace = workspaceState.items.firstOrNull { it.id == workspaceId }
        val items = if (memberState.workspaceId == workspaceId) memberState.items else emptyList()
        val email = (sessionState as? SessionState.SignedIn)?.account?.email
        val actor = workspace?.let { MemberRules.effectiveRole(it, items, email) }
            ?: MemberRules.selfOf(items, email)?.role
            ?: WorkspaceRole.UNKNOWN
        return Context(workspace, items, MemberRules.selfOf(items, email), actor)
    }

    private fun toUi(context: Context, memberState: MembersState, local: Transient): WorkspaceMembersUiState {
        val loaded = memberState.loaded && memberState.workspaceId == workspaceId
        val phase = when {
            loaded -> if (context.items.isEmpty()) MembersPhase.EMPTY else MembersPhase.LIST
            local.refreshing -> MembersPhase.LOADING
            local.error != null -> MembersPhase.ERROR
            else -> MembersPhase.LOADING
        }
        return WorkspaceMembersUiState(
            phase = phase,
            title = context.workspace?.name.orEmpty(),
            rows = context.items.map { member ->
                MemberRowUi(
                    userId = member.userId,
                    title = member.displayName,
                    email = member.email,
                    role = WorkspaceMembersScreenRules.roleLabel(member.role),
                    isSelf = member.userId == context.self?.userId,
                    actions = WorkspaceMembersScreenRules.actions(context.actor, member, context.self, context.isPersonal),
                )
            },
            ownerHint = loaded && WorkspaceMembersScreenRules.ownerHint(context.actor, context.isPersonal),
            refreshing = local.refreshing && loaded,
            error = local.error,
            dialog = local.dialog,
            submitting = local.submitting,
        )
    }

    private fun showDialog(dialog: MemberDialog) {
        update { it.copy(dialog = dialog) }
    }

    private fun update(change: (Transient) -> Transient) {
        transient.value = change(transient.value)
    }

    companion object {
        const val WORKSPACE_ID_ARG: String = "workspaceId"
        private const val STOP_TIMEOUT_MILLIS: Long = 5_000
        private val RELOAD_CODES = setOf(
            WorkspaceMemberErrorRules.OWNER_MUST_TRANSFER,
            WorkspaceMemberErrorRules.USER_NOT_WORKSPACE_MEMBER,
            WorkspaceMemberErrorRules.CANNOT_TRANSFER_TO_SELF,
            WorkspaceMemberErrorRules.CANNOT_CHANGE_OWNER_ROLE,
        )
    }
}
