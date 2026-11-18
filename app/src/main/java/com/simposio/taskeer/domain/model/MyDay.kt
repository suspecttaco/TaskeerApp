package com.simposio.taskeer.domain.model

import java.time.Instant

data class MyDayTask(
    val task: Task,
    val boardId: String?,
    val boardName: String?,
    val addedAt: Instant?,
    val listName: String? = null,
)

data class MyDay(
    val pinned: List<MyDayTask>,
    val assigned: List<MyDayTask>,
    val suggestions: List<MyDayTask> = emptyList(),
)
