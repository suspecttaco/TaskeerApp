package com.simposio.taskeer.ui.workspaces

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.ServerBoardsRepository
import com.simposio.taskeer.data.repository.WorkspaceRepository
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.workspace.ServerBoardsState
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.domain.workspace.WorkspaceRules
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ServerBoardsViewModel(
    savedStateHandle: SavedStateHandle,
    private val boards: ServerBoardsRepository,
    private val workspaces: WorkspaceRepository,
    // Ids de los tableros con copia local; la UI de tableros del servidor no conoce los repositorios locales.
    private val localBoardIds: suspend () -> Set<String>,
    private val session: StateFlow<SessionState>,
) : ViewModel() {

    private val workspaceId: String = requireNotNull(savedStateHandle[WORKSPACE_ID_ARG]) { "Falta el workspaceId de la ruta" }

    private data class Transient(val refreshing: Boolean = false, val error: BoardRoleErrorMessage? = null)

    private val transient = MutableStateFlow(Transient())
    private var opened = false

    // Canal y no estado: el aviso ocurre una vez y no debe repetirse al rotar.
    private val eventChannel = Channel<ServerBoardsEvent>(Channel.BUFFERED)

    val events: Flow<ServerBoardsEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<ServerBoardsUiState> = combine(boards.state, transient) { state, local ->
        toUi(state, local)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ServerBoardsUiState())

    init {
        viewModelScope.launch {
            session.collect { if (it is SessionState.SignedOut) eventChannel.send(ServerBoardsEvent.LeaveScreen) }
        }
    }

    fun onOpened() {
        if (opened) return
        opened = true
        if (!WorkspaceRules.isValidId(workspaceId)) {
            viewModelScope.launch { leaveBecauseGone(refreshWorkspaces = false) }
            return
        }
        load()
    }

    fun refresh() {
        if (transient.value.refreshing) return
        load()
    }

    fun openBoard(boardId: String) {
        val state = boards.state.value
        if (state.workspaceId != workspaceId || state.items.none { it.id == boardId }) return
        viewModelScope.launch {
            val route = ServerBoardRoutingRules.route(session.value is SessionState.SignedIn, boardId, localBoardIds())
            when (route) {
                is ServerBoardRoute.OpenKanban -> eventChannel.send(ServerBoardsEvent.OpenKanban(route.boardId))
                ServerBoardRoute.SyncFirst -> eventChannel.send(ServerBoardsEvent.NeedsSync)
                ServerBoardRoute.NotOffered -> Unit
            }
        }
    }

    private fun load() {
        transient.value = Transient(refreshing = true)
        viewModelScope.launch {
            try {
                val outcome = boards.load(workspaceId)
                if (outcome is WorkspaceOutcome.Failed) reportFailure(outcome)
            } finally {
                transient.value = transient.value.copy(refreshing = false)
            }
        }
    }

    private suspend fun reportFailure(failure: WorkspaceOutcome.Failed) {
        val message = BoardRoleErrorRules.messageFor(failure.error, failure.retryAfterSeconds)
        if (message == BoardRoleErrorMessage.BoardGone) {
            leaveBecauseGone()
        } else if (hasList()) {
            message?.let { eventChannel.send(ServerBoardsEvent.ShowError(it)) }
        } else {
            transient.value = transient.value.copy(error = message ?: BoardRoleErrorMessage.Generic)
        }
    }

    private fun hasList(): Boolean = boards.state.value.let { it.loaded && it.workspaceId == workspaceId }

    private suspend fun leaveBecauseGone(refreshWorkspaces: Boolean = true) {
        if (refreshWorkspaces) workspaces.refresh()
        eventChannel.send(ServerBoardsEvent.ShowError(BoardRoleErrorMessage.BoardGone))
        eventChannel.send(ServerBoardsEvent.LeaveScreen)
    }

    private fun toUi(state: ServerBoardsState, local: Transient): ServerBoardsUiState {
        val loaded = state.loaded && state.workspaceId == workspaceId
        val phase = when {
            loaded -> if (state.items.isEmpty()) ServerBoardsPhase.EMPTY else ServerBoardsPhase.LIST
            local.refreshing -> ServerBoardsPhase.LOADING
            local.error != null -> ServerBoardsPhase.ERROR
            else -> ServerBoardsPhase.LOADING
        }
        return ServerBoardsUiState(
            phase = phase,
            rows = if (loaded) state.items.map { BoardRowUi(it.id, it.name) } else emptyList(),
            refreshing = local.refreshing && loaded,
            error = local.error,
        )
    }

    companion object {
        const val WORKSPACE_ID_ARG: String = "workspaceId"
        private const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
