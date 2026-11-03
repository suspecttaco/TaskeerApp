package com.simposio.taskeer.data.local

import androidx.room.Embedded

data class MyDayRow(
    @Embedded val task: TaskEntity,
    val boardId: String,
    val boardName: String,
    val addedAt: Long?,
)
