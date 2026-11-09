package com.simposio.taskeer.domain.task

import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.BoardDestination
import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.ColumnDestination

fun buildBoardDestinations(
    boards: List<Board>,
    columns: List<Column>,
    countsByColumn: Map<String, Int>,
    includeDoneColumns: Boolean,
): List<BoardDestination> {
    val columnsByBoard = columns
        .filter { !it.isDeleted && (includeDoneColumns || !it.isDoneColumn) }
        .sortedWith(compareBy({ it.position }, { it.id }))
        .groupBy { it.boardId }
    return boards
        .filter { !it.isDeleted }
        .sortedWith(compareBy({ it.createdAt }, { it.id }))
        .map { board ->
            BoardDestination(
                board = board,
                columns = columnsByBoard[board.id].orEmpty().map { column ->
                    ColumnDestination(column, countsByColumn[column.id] ?: 0)
                },
            )
        }
}
