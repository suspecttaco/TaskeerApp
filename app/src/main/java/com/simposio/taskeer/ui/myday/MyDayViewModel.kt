package com.simposio.taskeer.ui.myday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.myday.MyDayFilter
import com.simposio.taskeer.domain.myday.MyDayItem
import com.simposio.taskeer.domain.myday.MyDaySort
import com.simposio.taskeer.domain.myday.applyMyDay
import com.simposio.taskeer.domain.myday.mergeMyDay
import com.simposio.taskeer.domain.task.isOverdue
import com.simposio.taskeer.ui.tasks.DUE_DATE_TIME_FORMATTER
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MyDayViewModel(
    private val repository: MyDayRepository,
    private val dateTimeConverter: DateTimeConverter,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    private val error = MutableStateFlow<DomainError?>(null)
    private val sort = MutableStateFlow(MyDaySort.DUE_DATE)
    private val filter = MutableStateFlow(MyDayFilter.ALL)

    val uiState: StateFlow<MyDayUiState> = combine(
        repository.observeMyDay(),
        repository.observeOverdueCount(),
        sort,
        filter,
        error,
    ) { myDay, overdueCount, sort, filter, error ->
        val instant = now()
        val all = mergeMyDay(myDay)
        MyDayUiState(
            items = applyMyDay(all, filter, sort, instant).map { it.toUi(instant) },
            sort = sort,
            filter = filter,
            overdueCount = overdueCount,
            totalCount = all.size,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MyDayUiState())

    fun setSort(value: MyDaySort) {
        sort.value = value
    }

    fun setFilter(value: MyDayFilter) {
        filter.value = value
    }

    fun toggleMarked(taskId: String) {
        val isMarked = uiState.value.items.firstOrNull { it.id == taskId }?.isMarked ?: return
        viewModelScope.launch {
            when (val result = repository.setMarked(taskId, !isMarked)) {
                is DomainResult.Failure -> error.value = result.error
                is DomainResult.Success -> error.value = null
            }
        }
    }

    // Las listas ya excluyen columnas de completadas, por eso isDoneColumn es false.
    private fun MyDayItem.toUi(now: Instant): MyDayTaskUi = MyDayTaskUi(
        id = task.id,
        title = task.title,
        priority = task.priority,
        boardName = boardName,
        due = task.dueAt?.let { dateTimeConverter.toLocalDateTime(it).format(DUE_DATE_TIME_FORMATTER) },
        isOverdue = isOverdue(task.dueAt, isDoneColumn = false, now = now),
        isMarked = isMarked,
        isAssigned = isAssigned,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
