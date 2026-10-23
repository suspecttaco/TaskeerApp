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
import kotlinx.coroutines.launch

class BoardsViewModel(private val repository: BoardRepository) : ViewModel() {

    private val error = MutableStateFlow<DomainError?>(null)

    val uiState: StateFlow<BoardsUiState> = combine(
        repository.observeBoards(),
        error,
    ) { boards, error ->
        BoardsUiState(boards = boards, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BoardsUiState())

    fun createBoard(name: String) {
        launchOperation { repository.createBoard(name) }
    }

    fun renameBoard(id: String, name: String) {
        launchOperation { repository.renameBoard(id, name) }
    }

    fun deleteBoard(id: String) {
        launchOperation { repository.deleteBoard(id) }
    }

    private fun launchOperation(operation: suspend () -> DomainResult<*>) {
        viewModelScope.launch {
            error.value = null
            val result = operation()
            if (result is DomainResult.Failure) {
                error.value = result.error
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
