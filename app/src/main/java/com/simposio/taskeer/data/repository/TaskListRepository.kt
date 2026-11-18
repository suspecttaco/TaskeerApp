package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.TaskList
import com.simposio.taskeer.domain.model.TaskListSummary
import kotlinx.coroutines.flow.Flow

interface TaskListRepository {
    fun observeLists(): Flow<List<TaskListSummary>>

    fun observeDefaultList(): Flow<TaskList>

    suspend fun defaultListId(): String

    suspend fun createList(name: String): DomainResult<TaskList>

    suspend fun renameList(id: String, name: String): DomainResult<TaskList>

    suspend fun deleteList(id: String): DomainResult<Unit>
}
