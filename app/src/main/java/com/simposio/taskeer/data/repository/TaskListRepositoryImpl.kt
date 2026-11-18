package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.TaskListDao
import com.simposio.taskeer.data.local.toEntity
import com.simposio.taskeer.data.local.toTask
import com.simposio.taskeer.data.local.toTaskList
import com.simposio.taskeer.domain.list.LIST_NOT_FOUND
import com.simposio.taskeer.domain.list.checkNameAvailable
import com.simposio.taskeer.domain.list.checkNotLast
import com.simposio.taskeer.domain.list.defaultListOf
import com.simposio.taskeer.domain.list.movedToDefault
import com.simposio.taskeer.domain.list.nextListPosition
import com.simposio.taskeer.domain.list.newTaskList
import com.simposio.taskeer.domain.list.renamed
import com.simposio.taskeer.domain.list.softDeleted
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.TaskList
import com.simposio.taskeer.domain.model.TaskListSummary
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull

class TaskListRepositoryImpl(
    private val listDao: TaskListDao,
    private val taskDao: TaskDao,
    private val now: () -> Instant = Instant::now,
) : TaskListRepository {

    override fun observeLists(): Flow<List<TaskListSummary>> = combine(
        listDao.observeActive(),
        taskDao.observeActiveCountsByList(),
    ) { lists, counts ->
        val byList = counts.associate { it.listId to it.count }
        lists.map { TaskListSummary(it.toTaskList(), byList[it.id] ?: 0) }
    }

    override fun observeDefaultList(): Flow<TaskList> =
        listDao.observeActive().mapNotNull { lists -> defaultListOf(lists.map { it.toTaskList() }) }

    // La siembra y el rechazo de borrar la última lista garantizan que siempre hay una.
    override suspend fun defaultListId(): String =
        checkNotNull(defaultListOf(listDao.findActive().map { it.toTaskList() })) { "No hay ninguna lista activa" }.id

    override suspend fun createList(name: String): DomainResult<TaskList> {
        val active = listDao.findActive().map { it.toTaskList() }
        val created = when (val result = newTaskList(name, nextListPosition(active), now())) {
            is DomainResult.Failure -> return result
            is DomainResult.Success -> result.value
        }
        checkNameAvailable(created.name, active)?.let { return DomainResult.Failure(it) }
        listDao.upsert(created.toEntity())
        return DomainResult.Success(created)
    }

    override suspend fun renameList(id: String, name: String): DomainResult<TaskList> {
        val current = activeList(id) ?: return notFound()
        val renamed = when (val result = current.renamed(name, now())) {
            is DomainResult.Failure -> return result
            is DomainResult.Success -> result.value
        }
        val active = listDao.findActive().map { it.toTaskList() }
        checkNameAvailable(renamed.name, active, ignoringId = id)?.let { return DomainResult.Failure(it) }
        listDao.upsert(renamed.toEntity())
        return DomainResult.Success(renamed)
    }

    override suspend fun deleteList(id: String): DomainResult<Unit> {
        val current = activeList(id) ?: return notFound()
        val active = listDao.findActive().map { it.toTaskList() }
        checkNotLast(active, id)?.let { return DomainResult.Failure(it) }
        // Nunca la propia lista: se calcula entre las que quedan.
        val target = requireNotNull(defaultListOf(active.filter { it.id != id })).id
        val at = now()
        val tasks = listDao.findActiveTasks(id).map { it.toTask() }
        val moved = movedToDefault(tasks, target, listDao.countTasks(target), at)
        listDao.softDeleteMovingTasks(current.softDeleted(at).toEntity(), moved.map { it.toEntity() })
        return DomainResult.Success(Unit)
    }

    private suspend fun activeList(id: String): TaskList? =
        listDao.findById(id)?.toTaskList()?.takeUnless { it.isDeleted }

    private fun notFound(): DomainResult.Failure =
        DomainResult.Failure(DomainError.NotFound(LIST_NOT_FOUND, "La lista no existe"))
}
