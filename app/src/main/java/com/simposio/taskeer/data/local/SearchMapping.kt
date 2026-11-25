package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.search.SearchableTask

fun SearchTaskRow.toSearchableTask(): SearchableTask = SearchableTask(
    task = task.toTask(),
    boardId = boardId,
    boardName = boardName,
    columnName = columnName,
    listName = listName,
)
