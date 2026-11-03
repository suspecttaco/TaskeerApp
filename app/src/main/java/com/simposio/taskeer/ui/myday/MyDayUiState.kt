package com.simposio.taskeer.ui.myday

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.myday.MyDayFilter
import com.simposio.taskeer.domain.myday.MyDaySort

data class MyDayTaskUi(
    val id: String,
    val title: String,
    val priority: TaskPriority,
    val boardName: String,
    val due: String?,
    val isOverdue: Boolean,
    val isMarked: Boolean,
    val isAssigned: Boolean,
)

data class MyDayUiState(
    val items: List<MyDayTaskUi> = emptyList(),
    val sort: MyDaySort = MyDaySort.DUE_DATE,
    val filter: MyDayFilter = MyDayFilter.ALL,
    val overdueCount: Int = 0,
    val totalCount: Int = 0,
    val error: DomainError? = null,
)
