package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.ColumnDao
import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.toEntity
import com.simposio.taskeer.data.local.toTask
import com.simposio.taskeer.domain.column.COLUMN_NOT_FOUND
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.task.TASK_NOT_FOUND
import com.simposio.taskeer.domain.task.newTask
import com.simposio.taskeer.domain.task.softDeleted
import com.simposio.taskeer.domain.task.updated
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskRepositoryImpl(
    private val dao: TaskDao,
    private val columnDao: ColumnDao,
    private val now: () -> Instant = Instant::now,
) : TaskRepository {

    override fun observeTasks(boardId: String): Flow<List<Task>> =
        dao.observeByBoard(boardId).map { entities -> entities.map { it.toTask() } }

    override suspend fun getTask(id: String): DomainResult<Task> {
        val task = activeTask(id) ?: return taskNotFound()
        return DomainResult.Success(task)
    }

    override suspend fun createTask(columnId: String, draft: TaskDraft): DomainResult<Task> {
        if (!activeColumn(columnId)) {
            return columnNotFound()
        }
        val position = dao.findActiveByColumn(columnId).size
        return when (val result = newTask(columnId, draft, position, now())) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> {
                dao.upsert(result.value.toEntity())
                DomainResult.Success(result.value)
            }
        }
    }

    override suspend fun updateTask(id: String, draft: TaskDraft): DomainResult<Task> {
        val current = activeTask(id) ?: return taskNotFound()
        return when (val result = current.updated(draft, now())) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> {
                dao.upsert(result.value.toEntity())
                DomainResult.Success(result.value)
            }
        }
    }

    override suspend fun deleteTask(id: String): DomainResult<Unit> {
        val current = activeTask(id) ?: return taskNotFound()
        dao.upsert(current.softDeleted(now()).toEntity())
        return DomainResult.Success(Unit)
    }

    private suspend fun activeTask(id: String): Task? =
        dao.findById(id)?.toTask()?.takeUnless { it.isDeleted }

    private suspend fun activeColumn(columnId: String): Boolean =
        columnDao.findById(columnId)?.let { it.deletedAt == null } == true

    private fun columnNotFound(): DomainResult.Failure =
        DomainResult.Failure(DomainError.NotFound(COLUMN_NOT_FOUND, "La columna no existe"))

    private fun taskNotFound(): DomainResult.Failure =
        DomainResult.Failure(DomainError.NotFound(TASK_NOT_FOUND, "La tarea no existe"))
}
