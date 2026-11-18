package com.simposio.taskeer.testing

import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.data.local.TaskListDao
import com.simposio.taskeer.data.local.TaskListEntity
import com.simposio.taskeer.domain.list.SEEDED_TASK_LIST_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

// Filas de tareas compartidas entre los dobles de TaskDao y de TaskListDao, como la tabla real.
class FakeTaskStore {
    val rows = MutableStateFlow<Map<String, TaskEntity>>(emptyMap())

    fun put(task: TaskEntity) {
        rows.value = rows.value + (task.id to task)
    }
}

class FakeTaskListDao(
    val taskStore: FakeTaskStore = FakeTaskStore(),
    seedDefault: Boolean = true,
) : TaskListDao() {
    private val rows = MutableStateFlow<Map<String, TaskListEntity>>(emptyMap())
    private var inTransaction = false

    var writeCalls: Int = 0
        private set

    var transactionalCalls: Int = 0
        private set

    val stored: Map<String, TaskListEntity> get() = rows.value

    init {
        if (seedDefault) put(listEntity(SEEDED_TASK_LIST_ID, "Tareas", position = 0))
    }

    fun put(list: TaskListEntity) {
        rows.value = rows.value + (list.id to list)
    }

    override fun observeActive(): Flow<List<TaskListEntity>> = rows.map { stored ->
        stored.values
            .filter { it.deletedAt == null }
            .sortedWith(compareBy({ it.position }, { it.createdAt }, { it.id }))
    }

    override suspend fun findActive(): List<TaskListEntity> = rows.value.values.filter { it.deletedAt == null }

    override suspend fun findById(id: String): TaskListEntity? = rows.value[id]

    override suspend fun upsert(list: TaskListEntity) {
        if (!inTransaction) writeCalls++
        rows.value = rows.value + (list.id to list)
    }

    override suspend fun findActiveTasks(listId: String): List<TaskEntity> =
        taskStore.rows.value.values
            .filter { it.listId == listId && it.deletedAt == null }
            .sortedWith(compareBy({ it.position }, { it.createdAt }, { it.id }))

    override suspend fun countTasks(listId: String): Int =
        taskStore.rows.value.values.count { it.listId == listId && it.deletedAt == null }

    override suspend fun upsertTasks(tasks: List<TaskEntity>) {
        if (!inTransaction) writeCalls++
        taskStore.rows.value = taskStore.rows.value + tasks.associateBy { it.id }
    }

    override suspend fun softDeleteMovingTasks(deleted: TaskListEntity, moved: List<TaskEntity>) {
        transactionalCalls++
        inTransaction = true
        try {
            super.softDeleteMovingTasks(deleted, moved)
        } finally {
            inTransaction = false
        }
    }
}

fun listEntity(
    id: String,
    name: String,
    position: Int = 0,
    createdAt: Long = 0L,
    updatedAt: Long = 0L,
    deletedAt: Long? = null,
    pending: Boolean = false,
): TaskListEntity = TaskListEntity(
    id = id,
    name = name,
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pending = pending,
    deletedAt = deletedAt,
)
