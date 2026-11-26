package com.simposio.taskeer.ui.workspaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.WorkspaceRepository
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.workspace.NameCheck
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
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

class WorkspacesViewModel(
    private val workspaces: WorkspaceRepository,
    session: StateFlow<SessionState>,
) : ViewModel() {

    private data class Transient(
        val refreshing: Boolean = false,
        val error: WorkspaceErrorMessage? = null,
        val dialog: WorkspaceDialog = WorkspaceDialog.None,
        val submitting: Boolean = false,
        val dialogError: WorkspaceErrorMessage? = null,
    )

    private val transient = MutableStateFlow(Transient())
    private var opened = false

    // Canal y no estado: el aviso ocurre una vez y no debe repetirse al rotar.
    private val eventChannel = Channel<WorkspacesEvent>(Channel.BUFFERED)

    val events: Flow<WorkspacesEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<WorkspacesUiState> = combine(workspaces.state, transient) { state, local ->
        state.toUi(local)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WorkspacesUiState())

    init {
        viewModelScope.launch {
            session.collect { if (it is SessionState.SignedOut) eventChannel.send(WorkspacesEvent.LeaveScreen) }
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
                val outcome = workspaces.refresh()
                if (outcome is WorkspaceOutcome.Failed) reportRefreshFailure(outcome)
            } finally {
                update { it.copy(refreshing = false) }
            }
        }
    }

    private suspend fun reportRefreshFailure(failure: WorkspaceOutcome.Failed) {
        val message = WorkspaceErrorRules.messageFor(failure.error, failure.retryAfterSeconds, WorkspaceAction.LOAD)
        if (workspaces.state.value.loaded) {
            message?.let { eventChannel.send(WorkspacesEvent.ShowError(it)) }
        } else {
            update { it.copy(error = message ?: WorkspaceErrorMessage.Generic) }
        }
    }

    fun openCreate() {
        if (transient.value.submitting) return
        showDialog(WorkspaceDialog.Create)
    }

    fun openRename(id: String) {
        val row = workspaces.state.value.items.firstOrNull { it.id == id } ?: return
        if (transient.value.submitting || RowAction.RENAME !in WorkspacesScreenRules.rowActions(row)) return
        showDialog(WorkspaceDialog.Rename(id, row.name))
    }

    fun requestDelete(id: String) {
        val row = workspaces.state.value.items.firstOrNull { it.id == id } ?: return
        if (transient.value.submitting || RowAction.DELETE !in WorkspacesScreenRules.rowActions(row)) return
        showDialog(WorkspaceDialog.ConfirmDelete(id, row.name))
    }

    fun dismissDialog() {
        if (transient.value.submitting) return
        showDialog(WorkspaceDialog.None)
    }

    fun confirmName(raw: String) {
        val dialog = transient.value.dialog
        if (transient.value.submitting || (dialog !is WorkspaceDialog.Create && dialog !is WorkspaceDialog.Rename)) return
        val name = when (val check = WorkspaceRules.validateName(raw)) {
            is NameCheck.Valid -> check.name
            NameCheck.TooShort, NameCheck.TooLong -> {
                update { it.copy(dialogError = WorkspaceErrorMessage.InvalidName) }
                return
            }
        }
        submit {
            when (dialog) {
                is WorkspaceDialog.Rename -> workspaces.rename(dialog.id, name) to WorkspaceAction.RENAME
                else -> workspaces.create(name) to WorkspaceAction.CREATE
            }
        }
    }

    fun confirmDelete() {
        val dialog = transient.value.dialog as? WorkspaceDialog.ConfirmDelete ?: return
        if (transient.value.submitting) return
        submit { workspaces.delete(dialog.id) to WorkspaceAction.DELETE }
    }

    private fun submit(operation: suspend () -> Pair<WorkspaceOutcome, WorkspaceAction>) {
        update { it.copy(submitting = true, dialogError = null) }
        viewModelScope.launch {
            try {
                val (outcome, action) = operation()
                if (outcome is WorkspaceOutcome.Done) {
                    showDialog(WorkspaceDialog.None)
                } else if (outcome is WorkspaceOutcome.Failed) {
                    reportActionFailure(outcome, action)
                }
            } finally {
                update { it.copy(submitting = false) }
            }
        }
    }

    private suspend fun reportActionFailure(failure: WorkspaceOutcome.Failed, action: WorkspaceAction) {
        val message = WorkspaceErrorRules.messageFor(failure.error, failure.retryAfterSeconds, action)
        val inDialog = message == WorkspaceErrorMessage.PlanLimit || message == WorkspaceErrorMessage.CheckName
        when {
            message == null -> showDialog(WorkspaceDialog.None)
            inDialog && action != WorkspaceAction.DELETE -> update { it.copy(dialogError = message) }
            else -> {
                if (action == WorkspaceAction.DELETE || failure.error is DomainError.NotFound) showDialog(WorkspaceDialog.None)
                eventChannel.send(WorkspacesEvent.ShowError(message))
                if (action == WorkspaceAction.DELETE && failure.error is DomainError.Forbidden) refresh()
            }
        }
    }

    private fun showDialog(dialog: WorkspaceDialog) {
        update { it.copy(dialog = dialog, dialogError = null) }
    }

    private fun update(change: (Transient) -> Transient) {
        transient.value = change(transient.value)
    }

    private fun WorkspacesState.toUi(local: Transient): WorkspacesUiState {
        val phase = when {
            loaded -> if (items.isEmpty()) WorkspacesPhase.EMPTY else WorkspacesPhase.LIST
            local.refreshing -> WorkspacesPhase.LOADING
            local.error != null -> WorkspacesPhase.ERROR
            else -> WorkspacesPhase.LOADING
        }
        return WorkspacesUiState(
            phase = phase,
            rows = items.map { WorkspaceRowUi(it.id, it.name, WorkspacesScreenRules.roleLabel(it), WorkspacesScreenRules.rowActions(it)) },
            refreshing = local.refreshing && loaded,
            error = local.error,
            dialog = local.dialog,
            submitting = local.submitting,
            dialogError = local.dialogError,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
