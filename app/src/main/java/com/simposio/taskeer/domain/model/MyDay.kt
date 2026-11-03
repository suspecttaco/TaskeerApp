package com.simposio.taskeer.domain.model

import java.time.Instant

data class MyDayTask(
    val task: Task,
    val boardId: String,
    val boardName: String,
    val addedAt: Instant?,
)

data class MyDay(
    val pinned: List<MyDayTask>,
    val assigned: List<MyDayTask>,
)
