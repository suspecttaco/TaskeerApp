package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.ReminderCandidate
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskCreation
import com.simposio.taskeer.domain.model.TaskDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

class FakeReminderTaskRepository(
    candidates: List<ReminderCandidate> = emptyList(),
    var activeCount: Int = 0,
) : TaskRepository {
    override fun observeList(listId: String): Flow<List<Task>> = emptyFlow()

    override fun observeActiveCountsByColumn(): Flow<Map<String, Int>> = emptyFlow()

    override suspend fun assignToColumn(id: String, columnId: String): DomainResult<Task> =
        error("no se usa en este test")


    val candidates = MutableStateFlow(candidates)

    override fun observeReminderCandidates(): Flow<List<ReminderCandidate>> = candidates

    override suspend fun reminderCandidates(): List<ReminderCandidate> = candidates.value

    override suspend fun activeTaskCount(): Int = activeCount

    override fun observeTasks(boardId: String): Flow<List<Task>> = emptyFlow()

    override suspend fun getTask(id: String): DomainResult<Task> = unused()

    override suspend fun createTask(columnId: String?, draft: TaskDraft, listId: String?): DomainResult<Task> = unused()

    override suspend fun createTaskWithExtras(
        columnId: String?,
        creation: TaskCreation,
        listId: String?,
    ): DomainResult<Task> = unused()

    override suspend fun moveToList(id: String, listId: String): DomainResult<Task> = unused()

    override suspend fun updateTask(id: String, draft: TaskDraft): DomainResult<Task> = unused()

    override suspend fun moveTask(id: String, toColumnId: String, toIndex: Int): DomainResult<Task> =
        unused()

    override suspend fun setCompleted(id: String, completed: Boolean): DomainResult<Task> = unused()

    override fun observeCompletedCount(): Flow<Int> = error("no se usa")

    override suspend fun deleteCompleted(): DomainResult<Int> = error("no se usa")

    override suspend fun deleteTask(id: String): DomainResult<Unit> = unused()

    private fun unused(): Nothing = error("no se usa en este test")
}
