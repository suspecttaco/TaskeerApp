package com.simposio.taskeer.ui.search

import com.simposio.taskeer.domain.search.BoardHit
import com.simposio.taskeer.domain.search.ColumnHit
import com.simposio.taskeer.domain.search.ListHit
import com.simposio.taskeer.domain.search.SearchHit
import com.simposio.taskeer.domain.search.TaskHit

sealed interface SearchTarget {
    data class OpenBoard(val boardId: String) : SearchTarget

    data class OpenList(val listId: String) : SearchTarget

    data class OpenTask(val taskId: String) : SearchTarget
}

// La columna no tiene pantalla propia: abre el tablero que la contiene.
fun targetOf(hit: SearchHit): SearchTarget = when (hit) {
    is BoardHit -> SearchTarget.OpenBoard(hit.board.id)
    is ListHit -> SearchTarget.OpenList(hit.list.id)
    is ColumnHit -> SearchTarget.OpenBoard(hit.column.boardId)
    is TaskHit -> SearchTarget.OpenTask(hit.task.task.id)
}
