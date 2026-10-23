package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskDraft
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeTasks(boardId: String): Flow<List<Task>>

    suspend fun getTask(id: String): DomainResult<Task>

    suspend fun createTask(columnId: String, draft: TaskDraft): DomainResult<Task>

    suspend fun updateTask(id: String, draft: TaskDraft): DomainResult<Task>

    suspend fun deleteTask(id: String): DomainResult<Unit>
}
