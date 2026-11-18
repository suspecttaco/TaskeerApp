package com.simposio.taskeer.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.TaskListRepository
import com.simposio.taskeer.domain.list.defaultListOf
import com.simposio.taskeer.domain.list.effectiveListId
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TaskListsViewModel(
    private val repository: TaskListRepository,
    private val selection: ListSelection,
) : ViewModel() {

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<TaskListsUiState> = combine(
        repository.observeLists(),
        selection.selectedListId,
        local,
    ) { lists, selectedId, local ->
        val effective = effectiveListId(selectedId, lists.map { it.list })
        TaskListsUiState(lists = lists, selectedListId = effective, dialog = local.dialog, error = local.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TaskListsUiState())

    fun select(listId: String) = selection.select(listId)

    fun openCreate() {
        local.value = Local(dialog = ListDialog.Create)
    }

    fun openRename(listId: String) {
        val summary = uiState.value.lists.firstOrNull { it.list.id == listId } ?: return
        local.value = Local(dialog = ListDialog.Rename(listId, summary.list.name))
    }

    fun openDelete(listId: String) {
        val summary = uiState.value.lists.firstOrNull { it.list.id == listId } ?: return
        val remaining = uiState.value.lists.map { it.list }.filter { it.id != listId }
        val target = defaultListOf(remaining) ?: return
        local.value = Local(
            dialog = ListDialog.ConfirmDelete(listId, summary.list.name, summary.activeCount, target.name),
        )
    }

    fun submitName(text: String) {
        val dialog = local.value.dialog
        viewModelScope.launch {
            when (dialog) {
                ListDialog.Create -> when (val result = repository.createList(text)) {
                    is DomainResult.Failure -> fail(result.error)
                    is DomainResult.Success -> {
                        selection.select(result.value.id)
                        dismiss()
                    }
                }
                is ListDialog.Rename -> when (val result = repository.renameList(dialog.listId, text)) {
                    is DomainResult.Failure -> fail(result.error)
                    is DomainResult.Success -> dismiss()
                }
                else -> Unit
            }
        }
    }

    fun confirmDelete() {
        val dialog = local.value.dialog as? ListDialog.ConfirmDelete ?: return
        viewModelScope.launch {
            when (val result = repository.deleteList(dialog.listId)) {
                is DomainResult.Failure -> fail(result.error)
                is DomainResult.Success -> {
                    if (selection.selectedListId.value == dialog.listId) selection.select(null)
                    dismiss()
                }
            }
        }
    }

    fun dismiss() {
        local.value = Local()
    }

    private fun fail(error: DomainError) {
        local.update { it.copy(error = error) }
    }

    private data class Local(val dialog: ListDialog = ListDialog.None, val error: DomainError? = null)

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
