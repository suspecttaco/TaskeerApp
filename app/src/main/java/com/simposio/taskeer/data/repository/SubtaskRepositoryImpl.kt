package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.SubtaskDao
import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.toEntity
import com.simposio.taskeer.data.local.toSubtask
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.Subtask
import com.simposio.taskeer.domain.model.SubtaskDraft
import com.simposio.taskeer.domain.model.SubtaskProgress
import com.simposio.taskeer.domain.subtask.SUBTASK_NOT_FOUND
import com.simposio.taskeer.domain.subtask.newSubtask
import com.simposio.taskeer.domain.subtask.softDeleted
import com.simposio.taskeer.domain.subtask.updated
import com.simposio.taskeer.domain.subtask.withCompleted
import com.simposio.taskeer.domain.task.TASK_NOT_FOUND
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class SubtaskRepositoryImpl(
    private val dao: SubtaskDao,
    private val taskDao: TaskDao,
    private val now: () -> Instant = Instant::now,
) : SubtaskRepository {

    override fun observeSubtasks(taskId: String): Flow<List<Subtask>> =
        dao.observeByTask(taskId).map { entities -> entities.map { it.toSubtask() } }

    override fun observeSubtaskProgress(taskId: String): Flow<SubtaskProgress> =
        combine(dao.observeTotalCount(taskId), dao.observeCompletedCount(taskId)) { total, completed ->
            SubtaskProgress(total = total, completed = completed)
        }

    override suspend fun createSubtask(taskId: String, draft: SubtaskDraft): DomainResult<Subtask> {
        if (taskDao.findById(taskId)?.let { it.deletedAt == null } != true) {
            return DomainResult.Failure(DomainError.NotFound(TASK_NOT_FOUND, "La tarea no existe"))
        }
        val position = dao.findActiveByTask(taskId).size
        return persist(newSubtask(taskId, draft, position, now()))
    }

    override suspend fun updateSubtask(id: String, draft: SubtaskDraft): DomainResult<Subtask> {
        val current = activeSubtask(id) ?: return subtaskNotFound()
        return persist(current.updated(draft, now()))
    }

    override suspend fun setSubtaskCompleted(
        id: String,
        completed: Boolean,
    ): DomainResult<Subtask> {
        val current = activeSubtask(id) ?: return subtaskNotFound()
        return persist(DomainResult.Success(current.withCompleted(completed, now())))
    }

    override suspend fun deleteSubtask(id: String): DomainResult<Unit> {
        val current = activeSubtask(id) ?: return subtaskNotFound()
        dao.upsert(current.softDeleted(now()).toEntity())
        return DomainResult.Success(Unit)
    }

    private suspend fun persist(result: DomainResult<Subtask>): DomainResult<Subtask> {
        if (result is DomainResult.Success) {
            dao.upsert(result.value.toEntity())
        }
        return result
    }

    private suspend fun activeSubtask(id: String): Subtask? =
        dao.findById(id)?.toSubtask()?.takeUnless { it.isDeleted }

    private fun subtaskNotFound(): DomainResult.Failure =
        DomainResult.Failure(DomainError.NotFound(SUBTASK_NOT_FOUND, "La subtarea no existe"))
}
