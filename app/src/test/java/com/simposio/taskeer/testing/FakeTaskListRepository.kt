package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.TaskListRepository
import com.simposio.taskeer.domain.list.SEEDED_TASK_LIST_ID
import com.simposio.taskeer.domain.list.LIST_NOT_FOUND
import com.simposio.taskeer.domain.list.checkNameAvailable
import com.simposio.taskeer.domain.list.checkNotLast
import com.simposio.taskeer.domain.list.defaultListOf
import com.simposio.taskeer.domain.list.nextListPosition
import com.simposio.taskeer.domain.list.newTaskList
import com.simposio.taskeer.domain.list.renamed
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.TaskList
import com.simposio.taskeer.domain.model.TaskListSummary
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapNotNull

fun listSummary(id: String, name: String, activeCount: Int = 0, position: Int = 0): TaskListSummary = TaskListSummary(
    TaskList(name, position, Instant.EPOCH, SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = null)),
    activeCount,
)

class FakeTaskListRepository(
    initial: List<TaskListSummary> = listOf(listSummary(SEEDED_TASK_LIST_ID, "Tareas")),
) : TaskListRepository {
    val lists = MutableStateFlow(initial)
    val created = mutableListOf<String>()
    val renamed = mutableListOf<Pair<String, String>>()
    val deleted = mutableListOf<String>()
    var deleteFailure: DomainError? = null

    override fun observeLists(): Flow<List<TaskListSummary>> = lists

    override fun observeDefaultList(): Flow<TaskList> =
        lists.mapNotNull { summaries -> defaultListOf(summaries.map { it.list }) }

    override suspend fun defaultListId(): String = checkNotNull(defaultListOf(lists.value.map { it.list })).id

    override suspend fun createList(name: String): DomainResult<TaskList> {
        created += name
        val current = lists.value.map { it.list }
        val list = when (val result = newTaskList(name, nextListPosition(current), Instant.EPOCH)) {
            is DomainResult.Failure -> return result
            is DomainResult.Success -> result.value
        }
        checkNameAvailable(list.name, current)?.let { return DomainResult.Failure(it) }
        lists.value = lists.value + TaskListSummary(list, 0)
        return DomainResult.Success(list)
    }

    override suspend fun renameList(id: String, name: String): DomainResult<TaskList> {
        renamed += id to name
        val summary = lists.value.firstOrNull { it.list.id == id } ?: return notFound()
        val renamedList = when (val result = summary.list.renamed(name, Instant.EPOCH)) {
            is DomainResult.Failure -> return result
            is DomainResult.Success -> result.value
        }
        checkNameAvailable(renamedList.name, lists.value.map { it.list }, ignoringId = id)
            ?.let { return DomainResult.Failure(it) }
        lists.value = lists.value.map { if (it.list.id == id) it.copy(list = renamedList) else it }
        return DomainResult.Success(renamedList)
    }

    override suspend fun deleteList(id: String): DomainResult<Unit> {
        deleted += id
        deleteFailure?.let { return DomainResult.Failure(it) }
        val summary = lists.value.firstOrNull { it.list.id == id } ?: return notFound()
        checkNotLast(lists.value.map { it.list }, id)?.let { return DomainResult.Failure(it) }
        lists.value = lists.value.filter { it.list.id != id }
        return DomainResult.Success(Unit)
    }

    private fun notFound(): DomainResult.Failure =
        DomainResult.Failure(DomainError.NotFound(LIST_NOT_FOUND, "La lista no existe"))
}
