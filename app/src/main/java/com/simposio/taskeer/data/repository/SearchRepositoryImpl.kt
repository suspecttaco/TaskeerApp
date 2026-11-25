package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.BoardDao
import com.simposio.taskeer.data.local.ColumnDao
import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.TaskListDao
import com.simposio.taskeer.data.local.toBoard
import com.simposio.taskeer.data.local.toColumn
import com.simposio.taskeer.data.local.toSearchableTask
import com.simposio.taskeer.data.local.toTaskList
import com.simposio.taskeer.domain.search.SearchIndex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SearchRepositoryImpl(
    private val boardDao: BoardDao,
    private val columnDao: ColumnDao,
    private val taskDao: TaskDao,
    private val taskListDao: TaskListDao,
) : SearchRepository {

    override fun observeIndex(): Flow<SearchIndex> = combine(
        boardDao.observeActive(),
        taskListDao.observeActive(),
        columnDao.observeAllActive(),
        taskDao.observeSearchable(),
    ) { boards, lists, columns, tasks ->
        SearchIndex(
            boards = boards.map { it.toBoard() },
            lists = lists.map { it.toTaskList() },
            columns = columns.map { it.toColumn() },
            tasks = tasks.map { it.toSearchableTask() },
        )
    }
}
