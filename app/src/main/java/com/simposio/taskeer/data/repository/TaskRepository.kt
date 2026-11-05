package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.ReminderCandidate
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskCreation
import com.simposio.taskeer.domain.model.TaskDraft
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeTasks(boardId: String): Flow<List<Task>>

    fun observeReminderCandidates(): Flow<List<ReminderCandidate>>

    suspend fun reminderCandidates(): List<ReminderCandidate>

    suspend fun activeTaskCount(): Int

    suspend fun getTask(id: String): DomainResult<Task>

    suspend fun createTask(columnId: String, draft: TaskDraft): DomainResult<Task>

    suspend fun createTaskWithExtras(columnId: String, creation: TaskCreation): DomainResult<Task>

    suspend fun updateTask(id: String, draft: TaskDraft): DomainResult<Task>

    suspend fun moveTask(id: String, toColumnId: String, toIndex: Int): DomainResult<Task>

    suspend fun deleteTask(id: String): DomainResult<Unit>
}
