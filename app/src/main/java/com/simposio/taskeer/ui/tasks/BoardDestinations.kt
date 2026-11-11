package com.simposio.taskeer.ui.tasks

import com.simposio.taskeer.data.repository.BoardRepository
import com.simposio.taskeer.data.repository.ColumnRepository
import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.model.BoardDestination
import com.simposio.taskeer.domain.task.buildBoardDestinations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

fun observeBoardDestinations(
    boardRepository: BoardRepository,
    columnRepository: ColumnRepository,
    taskRepository: TaskRepository,
    includeDoneColumns: Boolean,
): Flow<List<BoardDestination>> = combine(
    boardRepository.observeBoards(),
    columnRepository.observeAllColumns(),
    taskRepository.observeActiveCountsByColumn(),
) { boards, columns, counts ->
    buildBoardDestinations(boards, columns, counts, includeDoneColumns)
}
