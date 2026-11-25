package com.simposio.taskeer.domain.search

import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskList

data class SearchableTask(
    val task: Task,
    val boardId: String?,
    val boardName: String?,
    val columnName: String?,
    val listName: String?,
)

data class SearchIndex(
    val boards: List<Board>,
    val lists: List<TaskList>,
    val columns: List<Column>,
    val tasks: List<SearchableTask>,
)

sealed interface SearchHit {
    val id: String
}

data class BoardHit(val board: Board) : SearchHit {
    override val id: String get() = board.id
}

data class ListHit(val list: TaskList, val openTasks: Int) : SearchHit {
    override val id: String get() = list.id
}

data class ColumnHit(val column: Column, val boardName: String) : SearchHit {
    override val id: String get() = column.id
}

data class TaskHit(val task: SearchableTask, val inTitle: Boolean) : SearchHit {
    override val id: String get() = task.task.id
}

data class SearchResults(
    val boards: List<BoardHit>,
    val lists: List<ListHit>,
    val columns: List<ColumnHit>,
    val tasks: List<TaskHit>,
) {
    val total: Int get() = boards.size + lists.size + columns.size + tasks.size
    val isEmpty: Boolean get() = total == 0

    companion object {
        val Empty = SearchResults(emptyList(), emptyList(), emptyList(), emptyList())
    }
}
