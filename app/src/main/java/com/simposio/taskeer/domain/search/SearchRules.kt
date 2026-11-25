package com.simposio.taskeer.domain.search

import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.TaskList

object SearchRules {
    const val DEBOUNCE_MILLIS = 250L
}

fun search(query: String, index: SearchIndex): SearchResults {
    val terms = searchTerms(query)
    if (terms.isEmpty()) return SearchResults.Empty

    fun matches(text: String): Boolean = normalizeForSearch(text).let { haystack -> terms.all { it in haystack } }

    // El repositorio ya filtra lo borrado; se repite aquí para que search() sea correcta por sí sola.
    val boardNames = index.boards.filter { !it.isDeleted }.associate { it.id to it.name }

    val boards = index.boards
        .filter { !it.isDeleted && matches(it.name) }
        .sortedWith(compareBy<Board> { normalizeForSearch(it.name) }.thenBy { it.id })
        .map(::BoardHit)

    val openTasksByList = index.tasks
        .filter { !it.task.isDeleted && !it.task.isCompleted }
        .mapNotNull { it.task.listId }
        .groupingBy { it }
        .eachCount()

    val lists = index.lists
        .filter { !it.isDeleted && matches(it.name) }
        .sortedWith(compareBy<TaskList> { it.position }.thenBy { normalizeForSearch(it.name) }.thenBy { it.id })
        .map { ListHit(it, openTasksByList[it.id] ?: 0) }

    val columns = index.columns
        .filter { !it.isDeleted && it.boardId in boardNames && matches(it.name) }
        .map { ColumnHit(it, boardNames.getValue(it.boardId)) }
        .sortedWith(
            compareBy<ColumnHit> { normalizeForSearch(it.boardName) }
                .thenBy { it.column.position }
                .thenBy { it.id },
        )

    val tasks = index.tasks
        .filter { !it.task.isDeleted && matches("${it.task.title} ${it.task.description.orEmpty()}") }
        .map { TaskHit(it, inTitle = matches(it.task.title)) }
        .sortedWith(
            compareBy<TaskHit> { !it.inTitle }
                .thenBy { it.task.task.isCompleted }
                .thenBy { normalizeForSearch(it.task.task.title) }
                .thenBy { it.id },
        )

    return SearchResults(boards, lists, columns, tasks)
}
