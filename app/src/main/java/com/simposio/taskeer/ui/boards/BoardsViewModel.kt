package com.simposio.taskeer.ui.boards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.BoardRepository
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BoardsViewModel(private val repository: BoardRepository) : ViewModel() {

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<BoardsUiState> = combine(
        repository.observeBoards(),
        local,
    ) { boards, local ->
        BoardsUiState(
            boards = boards,
            dialog = local.dialog,
            error = local.error,
            deletedBoardId = local.deletedBoardId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BoardsUiState())

    fun openCreate() {
        local.value = Local(dialog = BoardDialog.Create)
    }

    fun openRename(boardId: String) {
        val board = uiState.value.boards.firstOrNull { it.id == boardId } ?: return
        local.value = Local(dialog = BoardDialog.Rename(boardId, board.name))
    }

    fun openDelete(boardId: String) {
        val board = uiState.value.boards.firstOrNull { it.id == boardId } ?: return
        local.value = Local(dialog = BoardDialog.ConfirmDelete(boardId, board.name))
    }

    fun submitName(text: String) {
        val dialog = local.value.dialog
        viewModelScope.launch {
            val result = when (dialog) {
                BoardDialog.Create -> repository.createBoard(text)
                is BoardDialog.Rename -> repository.renameBoard(dialog.boardId, text)
                else -> return@launch
            }
            when (result) {
                is DomainResult.Failure -> fail(result.error)
                is DomainResult.Success -> dismiss()
            }
        }
    }

    fun confirmDelete() {
        val dialog = local.value.dialog as? BoardDialog.ConfirmDelete ?: return
        viewModelScope.launch {
            when (val result = repository.deleteBoard(dialog.boardId)) {
                is DomainResult.Failure -> fail(result.error)
                is DomainResult.Success -> local.value = Local(deletedBoardId = dialog.boardId)
            }
        }
    }

    fun dismiss() {
        local.update { Local(deletedBoardId = it.deletedBoardId) }
    }

    private fun fail(error: DomainError) {
        local.update { it.copy(error = error) }
    }

    private data class Local(
        val dialog: BoardDialog = BoardDialog.None,
        val error: DomainError? = null,
        val deletedBoardId: String? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
