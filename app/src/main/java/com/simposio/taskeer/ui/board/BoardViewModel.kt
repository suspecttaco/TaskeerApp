package com.simposio.taskeer.ui.board

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.ColumnRepository
import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.task.isOverdue
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class BoardViewModel(
    private val columnRepository: ColumnRepository,
    private val taskRepository: TaskRepository,
    private val dateTimeConverter: DateTimeConverter,
    savedStateHandle: SavedStateHandle,
    private val myDayRepository: MyDayRepository,
    myDayTaskIds: Flow<Set<String>> = myDayRepository.observeMarkedTaskIds(),
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    val boardId: String =
        requireNotNull(savedStateHandle[BOARD_ID_ARG]) { "Falta el boardId de la ruta" }

    private val pendingMove = MutableStateFlow<PendingMove?>(null)
    private val error = MutableStateFlow<DomainError?>(null)
    private val newColumn = MutableStateFlow(NewColumnUiState())

    val uiState: StateFlow<BoardUiState> = combine(
        columnRepository.observeColumns(boardId),
        taskRepository.observeTasks(boardId),
        myDayTaskIds,
        pendingMove,
        combine(error, newColumn) { error, newColumn -> error to newColumn },
    ) { columns, tasks, myDay, move, (error, newColumn) ->
        val ordered = tasks.sortedWith(compareBy({ it.position }, { it.id }))
        val byColumn = ordered.groupBy { it.columnId }.toMutableMap()
        if (move != null && !move.isReflectedIn(ordered)) {
            byColumn.withMove(move)
        }
        val instant = now()
        BoardUiState(
            columns = columns.sortedWith(compareBy({ it.position }, { it.id })).map { column ->
                BoardColumnUi(column, byColumn[column.id].orEmpty().map { it.toCard(myDay, column.isDoneColumn, instant) })
            },
            error = error,
            newColumn = newColumn,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BoardUiState())

    fun moveTask(taskId: String, toColumnId: String, toIndex: Int) {
        val current = uiState.value.columns
        val fromColumn = current.firstOrNull { column -> column.cards.any { it.id == taskId } }
        if (fromColumn != null &&
            fromColumn.id == toColumnId &&
            fromColumn.cards.indexOfFirst { it.id == taskId } == toIndex
        ) {
            return
        }
        val move = PendingMove(taskId, toColumnId, toIndex)
        pendingMove.value = move
        viewModelScope.launch {
            when (val result = taskRepository.moveTask(taskId, toColumnId, toIndex)) {
                is DomainResult.Failure -> {
                    pendingMove.value = null
                    error.value = result.error
                }
                is DomainResult.Success -> {
                    error.value = null
                    // Mantiene la tarjeta en destino hasta que Room emite el orden persistido, para evitar un parpadeo.
                    withTimeoutOrNull(SETTLE_TIMEOUT_MILLIS) {
                        taskRepository.observeTasks(boardId).first { tasks ->
                            move.isReflectedIn(tasks.sortedWith(compareBy({ it.position }, { it.id })))
                        }
                    }
                    if (pendingMove.value == move) {
                        pendingMove.value = null
                    }
                }
            }
        }
    }

    fun toggleMyDay(taskId: String) {
        val inMyDay = uiState.value.columns.flatMap { it.cards }.firstOrNull { it.id == taskId }?.inMyDay ?: return
        viewModelScope.launch {
            when (val result = myDayRepository.setMarked(taskId, !inMyDay)) {
                is DomainResult.Failure -> error.value = result.error
                is DomainResult.Success -> error.value = null
            }
        }
    }

    fun openNewColumn() {
        newColumn.value = NewColumnUiState(isOpen = true)
    }

    fun onNewColumnNameChange(name: String) {
        newColumn.update { it.copy(name = name, error = null) }
    }

    fun saveNewColumn() {
        val name = newColumn.value.name
        viewModelScope.launch {
            when (val result = columnRepository.createColumn(boardId, name)) {
                is DomainResult.Success -> newColumn.value = NewColumnUiState()
                is DomainResult.Failure -> newColumn.update { it.copy(error = result.error) }
            }
        }
    }

    fun dismissNewColumn() {
        newColumn.value = NewColumnUiState()
    }

    private fun Task.toCard(myDay: Set<String>, isDoneColumn: Boolean, now: Instant): TaskCardUi = TaskCardUi(
        id = id,
        title = title,
        priority = priority,
        dueAt = dueAt,
        dueAtLocal = dueAt?.let(dateTimeConverter::toLocalDateTime),
        assigneeId = assigneeId,
        assigneeIsMe = assigneeId == LocalUser.ID,
        inMyDay = id in myDay,
        isOverdue = isOverdue(dueAt, isDoneColumn, now),
    )

    private data class PendingMove(val taskId: String, val toColumnId: String, val toIndex: Int) {
        fun isReflectedIn(ordered: List<Task>): Boolean =
            ordered.filter { it.columnId == toColumnId }.indexOfFirst { it.id == taskId } == toIndex
    }

    private fun MutableMap<String, List<Task>>.withMove(move: PendingMove) {
        val moved = values.flatten().firstOrNull { it.id == move.taskId } ?: return
        keys.toList().forEach { key -> put(key, getValue(key).filter { it.id != move.taskId }) }
        val target = getOrDefault(move.toColumnId, emptyList()).toMutableList()
        target.add(move.toIndex.coerceIn(0, target.size), moved)
        put(move.toColumnId, target)
    }

    companion object {
        const val BOARD_ID_ARG: String = "boardId"

        private const val STOP_TIMEOUT_MILLIS: Long = 5_000
        private const val SETTLE_TIMEOUT_MILLIS: Long = 2_000
    }
}
