package com.simposio.taskeer.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.task.isOverdue
import com.simposio.taskeer.ui.tasks.DUE_DATE_TIME_FORMATTER
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InboxViewModel(
    taskRepository: TaskRepository,
    private val myDayRepository: MyDayRepository,
    private val dateTimeConverter: DateTimeConverter,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    private val error = MutableStateFlow<DomainError?>(null)

    val uiState: StateFlow<InboxUiState> = combine(
        taskRepository.observeInbox(),
        myDayRepository.observeMarkedTaskIds(),
        error,
    ) { tasks, marked, error ->
        val instant = now()
        InboxUiState(items = tasks.map { it.toUi(it.id in marked, instant) }, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), InboxUiState())

    fun toggleMarked(taskId: String) {
        val isMarked = uiState.value.items.firstOrNull { it.id == taskId }?.isMarked ?: return
        viewModelScope.launch {
            when (val result = myDayRepository.setMarked(taskId, !isMarked)) {
                is DomainResult.Failure -> error.value = result.error
                is DomainResult.Success -> error.value = null
            }
        }
    }

    private fun Task.toUi(isMarked: Boolean, now: Instant): InboxTaskUi = InboxTaskUi(
        id = id,
        title = title,
        priority = priority,
        due = dueAt?.let { dateTimeConverter.toLocalDateTime(it).format(DUE_DATE_TIME_FORMATTER) },
        isOverdue = isOverdue(dueAt, isDoneColumn = false, now = now),
        isMarked = isMarked,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
