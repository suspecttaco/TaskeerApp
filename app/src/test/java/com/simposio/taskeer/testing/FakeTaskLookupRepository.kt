package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.ReminderCandidate
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class FakeTaskLookupRepository : TaskRepository {

    val existing: MutableMap<String, Task> = mutableMapOf()

    override suspend fun getTask(id: String): DomainResult<Task> =
        existing[id]?.let { DomainResult.Success(it) }
            ?: DomainResult.Failure(DomainError.NotFound("TASK_NOT_FOUND", "La tarea no existe"))

    override fun observeTasks(boardId: String): Flow<List<Task>> = emptyFlow()

    override fun observeReminderCandidates(): Flow<List<ReminderCandidate>> = emptyFlow()

    override suspend fun reminderCandidates(): List<ReminderCandidate> = unused()

    override suspend fun activeTaskCount(): Int = unused()

    override suspend fun createTask(columnId: String, draft: TaskDraft): DomainResult<Task> = unused()

    override suspend fun updateTask(id: String, draft: TaskDraft): DomainResult<Task> = unused()

    override suspend fun moveTask(id: String, toColumnId: String, toIndex: Int): DomainResult<Task> =
        unused()

    override suspend fun deleteTask(id: String): DomainResult<Unit> = unused()

    private fun unused(): Nothing = error("no se usa en este test")
}
