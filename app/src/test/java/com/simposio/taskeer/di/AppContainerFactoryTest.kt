package com.simposio.taskeer.di

import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppContainerFactoryTest {

    @Test
    fun elConteoDeTareasPorColumna_usaElDaoDeTareas() = runTest {
        val dao = FakeTaskDao()
        dao.put(taskEntity("t1", columnId = "columna-1"))
        dao.put(taskEntity("t2", columnId = "columna-1"))
        dao.put(taskEntity("t3", columnId = "columna-2"))
        val count = taskCountByColumn(dao)

        assertEquals(2, count("columna-1"))
        assertEquals(1, count("columna-2"))
        assertEquals(0, count("columna-3"))
        assertEquals(listOf("columna-1", "columna-2", "columna-3"), dao.countedColumns)
    }

    private class FakeTaskDao : TaskDao() {
        private val rows = MutableStateFlow<Map<String, TaskEntity>>(emptyMap())

        val countedColumns: MutableList<String> = mutableListOf()

        fun put(task: TaskEntity) {
            rows.value = rows.value + (task.id to task)
        }

        override fun observeByBoard(boardId: String): Flow<List<TaskEntity>> = MutableStateFlow(emptyList())

        override suspend fun findActiveByColumn(columnId: String): List<TaskEntity> = emptyList()

        override suspend fun findById(id: String): TaskEntity? = rows.value[id]

        override suspend fun countByColumn(columnId: String): Int {
            countedColumns += columnId
            return rows.value.values.count { it.columnId == columnId }
        }

        override suspend fun upsert(task: TaskEntity) {
            rows.value = rows.value + (task.id to task)
        }
    }

    private fun taskEntity(id: String, columnId: String): TaskEntity = TaskEntity(
        id = id,
        columnId = columnId,
        title = "Tarea $id",
        description = null,
        priority = "MEDIUM",
        dueAt = null,
        assigneeId = null,
        position = 0,
        updatedAt = 0L,
        pending = true,
        deletedAt = null,
    )
}
