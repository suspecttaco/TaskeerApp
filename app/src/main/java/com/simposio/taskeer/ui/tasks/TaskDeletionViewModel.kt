package com.simposio.taskeer.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.task.UNDO_WINDOW
import java.time.Duration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PendingUndo(val token: Long, val taskIds: List<String>)

data class DeletionFailure(val error: DomainError, val whileUndoing: Boolean)

// El temporizador vive aquí y no en el snackbar: la duración de Material varía con la accesibilidad
// y debe coincidir con la gracia de sincronización de UNDO_WINDOW.
class TaskDeletionViewModel(
    private val taskRepository: TaskRepository,
    private val undoWindow: Duration = UNDO_WINDOW,
) : ViewModel() {

    private val _pending = MutableStateFlow<PendingUndo?>(null)
    val pending: StateFlow<PendingUndo?> = _pending.asStateFlow()

    private val _error = MutableStateFlow<DeletionFailure?>(null)
    val error: StateFlow<DeletionFailure?> = _error.asStateFlow()

    private var timer: Job? = null
    private var lastToken = 0L

    fun delete(taskId: String) {
        viewModelScope.launch {
            when (val result = taskRepository.deleteTask(taskId)) {
                is DomainResult.Failure -> _error.value = DeletionFailure(result.error, whileUndoing = false)
                is DomainResult.Success -> publishPending(listOf(taskId))
            }
        }
    }

    suspend fun deleteCompleted(): DomainResult<Unit> =
        when (val result = taskRepository.deleteCompleted()) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> {
                if (result.value.isNotEmpty()) publishPending(result.value)
                DomainResult.Success(Unit)
            }
        }

    fun undo() {
        val current = _pending.value ?: return
        timer?.cancel()
        _pending.value = null
        viewModelScope.launch {
            val result = taskRepository.restoreTasks(current.taskIds)
            if (result is DomainResult.Failure) _error.value = DeletionFailure(result.error, whileUndoing = true)
        }
    }

    fun consumeError() {
        _error.value = null
    }

    private fun publishPending(ids: List<String>) {
        timer?.cancel()
        val token = ++lastToken
        _pending.value = PendingUndo(token, ids)
        timer = viewModelScope.launch {
            delay(undoWindow.toMillis())
            if (_pending.value?.token == token) _pending.value = null
        }
    }
}
