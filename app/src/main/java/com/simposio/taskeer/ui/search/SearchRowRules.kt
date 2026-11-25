package com.simposio.taskeer.ui.search

import com.simposio.taskeer.domain.search.SearchableTask
import java.util.Locale

sealed interface TaskContext {
    data class InBoard(val board: String, val column: String) : TaskContext

    data class InList(val name: String) : TaskContext
}

fun taskContext(task: SearchableTask): TaskContext {
    val board = task.boardName
    val column = task.columnName
    return if (board != null && column != null) {
        TaskContext.InBoard(board, column)
    } else {
        TaskContext.InList(task.listName.orEmpty())
    }
}

fun rowDescription(kindLabel: String, title: String, context: String?, completedLabel: String?): String =
    buildString {
        append(kindLabel).append(": ").append(title)
        if (!context.isNullOrBlank()) append(", ").append(context)
        if (completedLabel != null) append(", ").append(completedLabel.lowercase(Locale.ROOT))
    }
