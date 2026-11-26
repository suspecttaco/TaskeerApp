package com.simposio.taskeer.ui.workspaces

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.BoardAccessRepository
import com.simposio.taskeer.data.repository.RoleChangeSignals
import com.simposio.taskeer.data.repository.WorkspaceMembersRepository
import com.simposio.taskeer.data.repository.WorkspaceRepository
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.workspace.BoardAccessState
import com.simposio.taskeer.domain.workspace.BoardMemberOverride
import com.simposio.taskeer.domain.workspace.BoardRoleRules
import com.simposio.taskeer.domain.workspace.MemberRules
import com.simposio.taskeer.domain.workspace.MembersState
import com.simposio.taskeer.domain.workspace.Workspace
import com.simposio.taskeer.domain.workspace.WorkspaceMember
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.domain.workspace.WorkspaceRules
import com.simposio.taskeer.domain.workspace.WorkspacesState
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BoardAccessViewModel(
    savedStateHandle: SavedStateHandle,
    private val access: BoardAccessRepository,
    private val members: WorkspaceMembersRepository,
    private val workspaces: WorkspaceRepository,
    private val signals: RoleChangeSignals,
    private val session: StateFlow<SessionState>,
) : ViewModel() {

    private val workspaceId: String = requireNotNull(savedStateHandle[WORKSPACE_ID_ARG]) { "Falta el workspaceId de la ruta" }
    private val boardId: String = requireNotNull(savedStateHandle[BOARD_ID_ARG]) { "Falta el boardId de la ruta" }

    private data class Transient(
        val opening: Boolean = false,
        val refreshing: Boolean = false,
        val error: BoardRoleErrorMessage? = null,
        val panelError: Boolean = false,
        val dialog: BoardDialog = BoardDialog.None,
        val submitting: Boolean = false,
    )

    // Foto coherente de lo que decide qué acciones se ofrecen.
    private class Context(
        val workspace: Workspace?,
        val items: List<WorkspaceMember>,
        val self: WorkspaceMember?,
        val workspaceRole: WorkspaceRole,
        val overrides: List<BoardMemberOverride>,
    ) {
        fun member(userId: String): WorkspaceMember? = items.firstOrNull { it.userId == userId }

        fun override(userId: String): BoardMemberOverride? = overrides.firstOrNull { it.userId == userId }
    }

    private val transient = MutableStateFlow(Transient())
    private var opened = false
    private var signalsJob: Job? = null
    private var signalErrorShown = false

    // Canal y no estado: el aviso ocurre una vez y no debe repetirse al rotar.
    private val eventChannel = Channel<BoardAccessEvent>(Channel.BUFFERED)

    val events: Flow<BoardAccessEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<BoardAccessUiState> = combine(
        access.state,
        members.state,
        workspaces.state,
        session,
        transient,
    ) { accessState, memberState, workspaceState, sessionState, local ->
        toUi(accessState, memberState, contextOf(accessState, memberState, workspaceState, sessionState), local)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BoardAccessUiState())

    init {
        viewModelScope.launch {
            session.collect {
                if (it is SessionState.SignedOut) {
                    signalsJob?.cancel()
                    eventChannel.send(BoardAccessEvent.LeaveScreen)
                }
            }
        }
    }

    fun onOpened() {
        if (opened) return
        opened = true
        if (!WorkspaceRules.isValidId(workspaceId) || !WorkspaceRules.isValidId(boardId)) {
            viewModelScope.launch { leaveBecauseGone() }
            return
        }
        update { it.copy(opening = true, error = null) }
        viewModelScope.launch {
            try {
                openBoard()
            } finally {
                update { it.copy(opening = false) }
            }
        }
    }

    fun refresh() {
        if (transient.value.refreshing || transient.value.opening) return
        update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            try {
                refreshAll(fromSignal = false)
            } finally {
                update { it.copy(refreshing = false) }
            }
        }
    }

    // Con el tablero sin leer no se pide el panel, pero sí se escucha la señal: una reconexión puede recuperarlo.
    private suspend fun openBoard() {
        val outcome = access.open(workspaceId, boardId)
        if (outcome is WorkspaceOutcome.Failed) {
            if (!reportFailure(outcome, fromSignal = false)) startSignals()
            return
        }
        if (ensureWorkspace()) loadPanel()
        startSignals()
    }

    // true = la pantalla se cierra (tablero o workspace desaparecidos).
    private suspend fun reportFailure(failure: WorkspaceOutcome.Failed, fromSignal: Boolean): Boolean {
        val message = BoardRoleErrorRules.messageFor(failure.error, failure.retryAfterSeconds) ?: return false
        if (message == BoardRoleErrorMessage.BoardGone) {
            leaveBecauseGone()
            return true
        }
        when {
            fromSignal -> if (!signalErrorShown) {
                signalErrorShown = true
                eventChannel.send(BoardAccessEvent.ShowError(message))
            }
            hasRole() -> eventChannel.send(BoardAccessEvent.ShowError(message))
            else -> update { it.copy(error = message) }
        }
        return false
    }

    private fun hasRole(): Boolean = access.state.value.let { it.matches() && it.effectiveRole != null }

    private fun BoardAccessState.matches(): Boolean = workspaceId == this@BoardAccessViewModel.workspaceId &&
        boardId == this@BoardAccessViewModel.boardId

    private suspend fun leaveBecauseGone() {
        eventChannel.send(BoardAccessEvent.ShowError(BoardRoleErrorMessage.BoardGone))
        eventChannel.send(BoardAccessEvent.LeaveScreen)
    }

    // El rol de workspace decide quién gestiona overrides: sin el workspace en memoria no se sabría.
    private suspend fun ensureWorkspace(): Boolean {
        if (workspaces.state.value.items.any { it.id == workspaceId }) return true
        val outcome = workspaces.refresh()
        if (outcome == WorkspaceOutcome.Done && workspaces.state.value.items.none { it.id == workspaceId }) {
            leaveBecauseGone()
            return false
        }
        return true
    }

    // El rol crudo puede venir de la lista de miembros (a veces obsoleta) o del workspace recién leído: basta uno.
    private fun shouldLoadPanel(): Boolean {
        val context = currentContext()
        return BoardRoleRules.canManageOverrides(context.workspaceRole) ||
            context.workspace?.let { BoardRoleRules.canManageOverrides(it.myRole) } == true
    }

    private suspend fun loadPanel() {
        if (!shouldLoadPanel()) return
        update { it.copy(panelError = false) }
        val (membersOutcome, overridesOutcome) = coroutineScope {
            val loadedMembers = async { members.load(workspaceId) }
            val loadedOverrides = async { access.loadOverrides() }
            loadedMembers.await() to loadedOverrides.await()
        }
        val failure = listOf(membersOutcome, overridesOutcome).filterIsInstance<WorkspaceOutcome.Failed>().firstOrNull() ?: return
        val message = BoardRoleErrorRules.messageFor(failure.error, failure.retryAfterSeconds)
        when {
            message == BoardRoleErrorMessage.BoardGone -> leaveBecauseGone()
            message == null -> Unit
            else -> {
                update { it.copy(panelError = true) }
                if (message == BoardRoleErrorMessage.ForbiddenAction) workspaces.refresh()
            }
        }
    }

    private suspend fun refreshAll(fromSignal: Boolean): WorkspaceOutcome {
        val previous = access.state.value.takeIf { it.matches() }?.effectiveRole
        val outcome = access.refresh()
        if (outcome is WorkspaceOutcome.Failed) {
            reportFailure(outcome, fromSignal)
            return outcome
        }
        signalErrorShown = false
        workspaces.refresh()
        loadPanel()
        BoardRoleRules.roleChangeNotice(previous, access.state.value.takeIf { it.matches() }?.effectiveRole)?.let {
            eventChannel.send(BoardAccessEvent.ShowMessage(BoardAccessNotice.RoleChanged(it)))
        }
        closeDialogIfNoLongerAllowed()
        return outcome
    }

    private fun startSignals() {
        if (signalsJob != null || session.value !is SessionState.SignedIn) return
        signalsJob = viewModelScope.launch {
            signals.signals.conflate().collect {
                delay(refreshOnSignal())
            }
        }
    }

    // Devuelve la pausa antes de atender la siguiente señal: el mínimo, o más si el servidor pidió esperar.
    private suspend fun refreshOnSignal(): Long {
        if (session.value !is SessionState.SignedIn) return BoardRoleRules.SIGNAL_REFRESH_PAUSE_MILLIS
        val outcome = refreshAll(fromSignal = true)
        val retryAfterMillis = (outcome as? WorkspaceOutcome.Failed)?.retryAfterSeconds?.times(MILLIS_PER_SECOND) ?: 0L
        return maxOf(BoardRoleRules.SIGNAL_REFRESH_PAUSE_MILLIS, retryAfterMillis)
    }

    fun openSetRole(userId: String) {
        val context = currentContext()
        val target = context.member(userId) ?: return
        if (transient.value.submitting || !canSet(context, target)) return
        val current = context.override(userId)?.role ?: target.role
        showDialog(BoardDialog.SetRole(userId, target.displayName, BoardRoleRules.assignableBoardRoles(current)))
    }

    fun setRole(role: WorkspaceRole) {
        val dialog = transient.value.dialog as? BoardDialog.SetRole ?: return
        if (transient.value.submitting) return
        val context = currentContext()
        val target = context.member(dialog.userId) ?: return
        val current = context.override(dialog.userId)?.role ?: target.role
        if (!canSet(context, target) || role !in BoardRoleRules.assignableBoardRoles(current)) return
        if (role == WorkspaceRole.ADMIN) {
            showDialog(BoardDialog.ConfirmAdmin(dialog.userId, target.displayName))
            return
        }
        submit { access.setOverride(dialog.userId, role) }
    }

    fun requestRemove(userId: String) {
        val context = currentContext()
        val target = context.member(userId) ?: return
        if (transient.value.submitting || !canRemove(context, target)) return
        showDialog(BoardDialog.ConfirmRemove(userId, target.displayName, access.state.value.boardName.orEmpty()))
    }

    fun confirm() {
        if (transient.value.submitting) return
        val context = currentContext()
        when (val dialog = transient.value.dialog) {
            is BoardDialog.ConfirmAdmin -> {
                val target = context.member(dialog.userId) ?: return
                val current = context.override(dialog.userId)?.role ?: target.role
                if (!canSet(context, target) || WorkspaceRole.ADMIN !in BoardRoleRules.assignableBoardRoles(current)) return
                submit { access.setOverride(dialog.userId, WorkspaceRole.ADMIN) }
            }
            is BoardDialog.ConfirmRemove -> {
                val target = context.member(dialog.userId) ?: return
                if (!canRemove(context, target)) return
                submit { access.removeOverride(dialog.userId) }
            }
            is BoardDialog.SetRole, BoardDialog.None -> Unit
        }
    }

    fun dismissDialog() {
        if (transient.value.submitting) return
        showDialog(BoardDialog.None)
    }

    private fun canSet(context: Context, target: WorkspaceMember): Boolean =
        BoardRoleRules.canSetOverride(context.workspaceRole, target, context.self)

    private fun canRemove(context: Context, target: WorkspaceMember): Boolean =
        BoardRoleRules.canRemoveOverride(context.workspaceRole, target, context.self, context.override(target.userId))

    private fun submit(call: suspend () -> WorkspaceOutcome) {
        update { it.copy(submitting = true) }
        viewModelScope.launch {
            try {
                when (val outcome = call()) {
                    WorkspaceOutcome.Done -> showDialog(BoardDialog.None)
                    is WorkspaceOutcome.Failed -> onFailure(outcome)
                }
            } finally {
                update { it.copy(submitting = false) }
            }
        }
    }

    private suspend fun onFailure(failure: WorkspaceOutcome.Failed) {
        val message = BoardRoleErrorRules.messageFor(failure.error, failure.retryAfterSeconds)
        showDialog(BoardDialog.None)
        when {
            message == null -> Unit
            message == BoardRoleErrorMessage.BoardGone -> leaveBecauseGone()
            else -> {
                eventChannel.send(BoardAccessEvent.ShowError(message))
                if (shouldReload(failure.error)) reloadAll()
            }
        }
    }

    // Rol de workspace obsoleto o miembro ya inexistente: lo que la pantalla ofrece no coincide con el servidor.
    private fun shouldReload(error: DomainError): Boolean = when (error) {
        is DomainError.Forbidden -> true
        is DomainError.Validation -> error.code == BoardRoleErrorRules.USER_NOT_WORKSPACE_MEMBER
        else -> false
    }

    private suspend fun reloadAll() {
        workspaces.refresh()
        if (shouldLoadPanel()) loadPanel() else members.load(workspaceId)
    }

    private fun closeDialogIfNoLongerAllowed() {
        val context = currentContext()
        val allowed = when (val dialog = transient.value.dialog) {
            BoardDialog.None -> true
            is BoardDialog.SetRole -> context.member(dialog.userId)?.let { canSet(context, it) } == true
            is BoardDialog.ConfirmAdmin -> context.member(dialog.userId)?.let { canSet(context, it) } == true
            is BoardDialog.ConfirmRemove -> context.member(dialog.userId)?.let { canRemove(context, it) } == true
        }
        if (!allowed && !transient.value.submitting) showDialog(BoardDialog.None)
    }

    private fun currentContext(): Context =
        contextOf(access.state.value, members.state.value, workspaces.state.value, session.value)

    private fun contextOf(
        accessState: BoardAccessState,
        memberState: MembersState,
        workspaceState: WorkspacesState,
        sessionState: SessionState,
    ): Context {
        val workspace = workspaceState.items.firstOrNull { it.id == workspaceId }
        val items = if (memberState.workspaceId == workspaceId) memberState.items else emptyList()
        val email = (sessionState as? SessionState.SignedIn)?.account?.email
        val role = workspace?.let { MemberRules.effectiveRole(it, items, email) }
            ?: MemberRules.selfOf(items, email)?.role
            ?: WorkspaceRole.UNKNOWN
        val overrides = if (accessState.matches()) accessState.overrides else emptyList()
        return Context(workspace, items, MemberRules.selfOf(items, email), role, overrides)
    }

    private fun toUi(
        accessState: BoardAccessState,
        memberState: MembersState,
        context: Context,
        local: Transient,
    ): BoardAccessUiState {
        val matches = accessState.matches()
        val role = if (matches) accessState.effectiveRole else null
        val phase = when {
            role != null -> BoardAccessPhase.READY
            local.opening || local.refreshing -> BoardAccessPhase.LOADING
            local.error != null -> BoardAccessPhase.ERROR
            else -> BoardAccessPhase.LOADING
        }
        val showPanel = phase == BoardAccessPhase.READY && BoardAccessScreenRules.showPanel(context.workspaceRole)
        val membersLoaded = memberState.loaded && memberState.workspaceId == workspaceId
        val panelReady = membersLoaded && matches && accessState.overridesLoaded
        val rows = if (showPanel && panelReady) {
            BoardRoleRules.rows(context.items, context.overrides, context.self)
                .map { BoardAccessScreenRules.toUi(context.workspaceRole, it, context.self) }
        } else {
            emptyList()
        }
        return BoardAccessUiState(
            phase = phase,
            title = if (matches) accessState.boardName.orEmpty() else "",
            roleLabel = BoardAccessScreenRules.effectiveRoleLabel(role),
            capabilities = BoardAccessScreenRules.capabilities(role),
            showPanel = showPanel,
            rows = rows,
            panelLoading = showPanel && !panelReady && !local.panelError,
            panelError = showPanel && local.panelError,
            noOtherMembers = showPanel && panelReady && rows.isEmpty(),
            refreshing = local.refreshing && phase == BoardAccessPhase.READY,
            error = local.error,
            dialog = local.dialog,
            submitting = local.submitting,
        )
    }

    private fun showDialog(dialog: BoardDialog) {
        update { it.copy(dialog = dialog) }
    }

    private fun update(change: (Transient) -> Transient) {
        transient.value = change(transient.value)
    }

    companion object {
        const val WORKSPACE_ID_ARG: String = "workspaceId"
        const val BOARD_ID_ARG: String = "boardId"
        private const val STOP_TIMEOUT_MILLIS: Long = 5_000
        private const val MILLIS_PER_SECOND: Long = 1_000
    }
}
