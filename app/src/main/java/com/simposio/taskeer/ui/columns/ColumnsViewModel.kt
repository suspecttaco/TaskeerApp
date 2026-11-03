package com.simposio.taskeer.ui.columns

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.ColumnRepository
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ColumnsViewModel(
    private val repository: ColumnRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val boardId: String =
        requireNotNull(savedStateHandle[BOARD_ID_ARG]) { "Falta el boardId de la ruta" }

    private val error = MutableStateFlow<DomainError?>(null)

    val uiState: StateFlow<ColumnsUiState> = combine(
        repository.observeColumns(boardId),
        error,
    ) { columns, error ->
        ColumnsUiState(columns = columns, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ColumnsUiState())

    fun createColumn(name: String) {
        launchOperation { repository.createColumn(boardId, name) }
    }

    fun renameColumn(id: String, name: String) {
        launchOperation { repository.renameColumn(id, name) }
    }

    fun moveColumn(id: String, toIndex: Int) {
        launchOperation { repository.moveColumn(id, toIndex) }
    }

    fun setWipLimit(id: String, limit: Int?) {
        launchOperation { repository.setWipLimit(id, limit) }
    }

    fun deleteColumn(id: String) {
        launchOperation { repository.deleteColumn(id) }
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

    companion object {
        const val BOARD_ID_ARG: String = "boardId"

        private const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
