package com.simposio.taskeer.data.local

import androidx.room.Embedded

data class SearchTaskRow(
    @Embedded val task: TaskEntity,
    val boardId: String?,
    val boardName: String?,
    val columnName: String?,
    val listName: String?,
)
