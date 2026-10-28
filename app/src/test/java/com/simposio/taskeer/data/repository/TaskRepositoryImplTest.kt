package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.BoardEntity
import com.simposio.taskeer.data.local.ColumnDao
import com.simposio.taskeer.data.local.ColumnEntity
import com.simposio.taskeer.data.local.SubtaskEntity
import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.model.TaskPriority
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRepositoryImplTest {

    private val now: Instant = Instant.parse("2026-10-21T10:00:00Z")
    private val later: Instant = now.plusSeconds(60)
    private val madrid = DateTimeConverter(TimeZoneProvider { ZoneId.of("Europe/Madrid") })

    @Test
    fun createTask_anhadeLaTareaAlFinalDeLaColumnaYPersiste() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t0", columnId = "columna-1", position = 0))
        taskDao.put(taskEntity("t1", columnId = "columna-1", position = 1))
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { now })

        val result = repository.createTask("columna-1", TaskDraft(title = "  Tarea nueva  "))

        val created = (result as DomainResult.Success).value
        assertEquals("Tarea nueva", created.title)
        assertEquals(2, created.position)
        assertEquals("columna-1", created.columnId)
        assertEquals(now, created.sync.updatedAt)
        assertTrue(created.sync.pending)
        assertNull(created.sync.deletedAt)

        val row = taskDao.stored.getValue(created.id)
        assertEquals("Tarea nueva", row.title)
        assertEquals(2, row.position)
        assertTrue(row.pending)
        assertNull(row.deletedAt)

        assertEquals(
            listOf("t0", "t1", created.id),
            repository.observeTasks(BOARD_ID).first().map { it.id },
        )
    }

    @Test
    fun createTask_sinTitulo_noEscribeEnElDao() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { now })

        val result = repository.createTask("columna-1", TaskDraft(title = "   "))

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.Validation)
        assertEquals("TASK_TITLE_REQUIRED", error.code)
        assertEquals(0, taskDao.writeCalls)
        assertTrue(taskDao.stored.isEmpty())
    }

    @Test
    fun createTask_deUnaColumnaDesconocida_fallaConNotFound() = runTest {
        val columnDao = FakeColumnDao()
        val taskDao = FakeTaskDao(columnDao)
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { now })

        val result = repository.createTask("columna-x", TaskDraft(title = "Tarea"))

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.NotFound)
        assertEquals("COLUMN_NOT_FOUND", error.code)
        assertEquals(0, taskDao.writeCalls)

        columnDao.put(columnEntity("columna-borrada", deleted = true))
        val deleted = repository.createTask("columna-borrada", TaskDraft(title = "Tarea"))

        val deletedError = (deleted as DomainResult.Failure).error
        assertTrue(deletedError is DomainError.NotFound)
        assertEquals("COLUMN_NOT_FOUND", deletedError.code)
        assertEquals(0, taskDao.writeCalls)
    }

    @Test
    fun createTask_persisteLaPrioridadYElAsignadoElegidos() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { now })

        val result = repository.createTask(
            "columna-1",
            TaskDraft(
                title = "Tarea",
                description = "Detalle",
                priority = TaskPriority.URGENT,
                assigneeId = LocalUser.ID,
            ),
        )

        val created = (result as DomainResult.Success).value
        assertEquals(TaskPriority.URGENT, created.priority)
        assertEquals(LocalUser.ID, created.assigneeId)
        assertEquals("Detalle", created.description)

        val row = taskDao.stored.getValue(created.id)
        assertEquals("URGENT", row.priority)
        assertEquals(LocalUser.ID, row.assigneeId)
        assertEquals("Detalle", row.description)

        val reread = repository.getTask(created.id)
        assertEquals(LocalUser.ID, (reread as DomainResult.Success).value.assigneeId)
        assertEquals(TaskPriority.URGENT, reread.value.priority)
    }

    @Test
    fun createTask_persisteElDueAtEnUtcSinDesplazarLaZona() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { now })
        val local = LocalDateTime.of(2026, 10, 22, 12, 30)
        val instant = madrid.toInstant(local)

        val result = repository.createTask("columna-1", TaskDraft(title = "Tarea", dueAt = instant))

        val created = (result as DomainResult.Success).value
        assertEquals(instant, created.dueAt)

        val row = taskDao.stored.getValue(created.id)
        assertEquals(instant.toEpochMilli(), row.dueAt)

        val reread = (repository.getTask(created.id) as DomainResult.Success).value
        assertEquals(local, madrid.toLocalDateTime(reread.dueAt!!))
    }

    @Test
    fun updateTask_persisteLosCamposYConservaElResto() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t1", columnId = "columna-1", position = 3, updatedAt = now))
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { later })
        val dueAt = Instant.parse("2026-10-23T09:00:00Z")

        val result = repository.updateTask(
            "t1",
            TaskDraft(
                title = "  Título nuevo ",
                description = "Detalle",
                priority = TaskPriority.HIGH,
                dueAt = dueAt,
                assigneeId = LocalUser.ID,
            ),
        )

        val updated = (result as DomainResult.Success).value
        assertEquals("Título nuevo", updated.title)
        assertEquals("Detalle", updated.description)
        assertEquals(TaskPriority.HIGH, updated.priority)
        assertEquals(dueAt, updated.dueAt)
        assertEquals(LocalUser.ID, updated.assigneeId)
        assertEquals("t1", updated.id)
        assertEquals("columna-1", updated.columnId)
        assertEquals(3, updated.position)
        assertNull(updated.sync.deletedAt)
        assertEquals(later, updated.sync.updatedAt)
        assertTrue(updated.sync.pending)

        val row = taskDao.stored.getValue("t1")
        assertEquals("Título nuevo", row.title)
        assertEquals(3, row.position)
        assertEquals(later.toEpochMilli(), row.updatedAt)
        assertTrue(row.pending)

        val reread = (repository.getTask("t1") as DomainResult.Success).value
        assertEquals("Título nuevo", reread.title)
        assertEquals(TaskPriority.HIGH, reread.priority)
    }

    @Test
    fun updateTask_sinTitulo_noEscribeEnElDao() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0, updatedAt = now))
        val before = taskDao.stored.getValue("t1")
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { later })

        val result = repository.updateTask("t1", TaskDraft(title = "  "))

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.Validation)
        assertEquals("TASK_TITLE_REQUIRED", error.code)
        assertEquals(before, taskDao.stored.getValue("t1"))
        assertEquals(0, taskDao.writeCalls)
    }

    @Test
    fun updateTask_deUnaTareaDesconocida_fallaConNotFound() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { later })

        val result = repository.updateTask("no-existe", TaskDraft(title = "Tarea"))

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.NotFound)
        assertEquals("TASK_NOT_FOUND", error.code)
        assertEquals(0, taskDao.writeCalls)
    }

    @Test
    fun getTask_deUnaTareaDesconocida_fallaConNotFound() = runTest {
        val columnDao = FakeColumnDao()
        val taskDao = FakeTaskDao(columnDao)
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { now })

        val result = repository.getTask("no-existe")

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.NotFound)
        assertEquals("TASK_NOT_FOUND", error.code)
    }

    @Test
    fun deleteTask_marcaDeletedAtYNoBorraLaFila() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0, updatedAt = now))
        taskDao.put(taskEntity("t2", columnId = "columna-1", position = 1, updatedAt = now))
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { later })

        val result = repository.deleteTask("t1")

        assertTrue(result is DomainResult.Success)
        val row = taskDao.stored.getValue("t1")
        assertEquals(later.toEpochMilli(), row.deletedAt)
        assertEquals(later.toEpochMilli(), row.updatedAt)
        assertTrue(row.pending)
        assertEquals("la fila no se borra físicamente", 2, taskDao.stored.size)
        assertEquals("las posiciones de las restantes no se renumeran", 1, taskDao.stored.getValue("t2").position)
        assertEquals(
            "la tarea borrada deja de emitirse",
            listOf("t2"),
            repository.observeTasks(BOARD_ID).first().map { it.id },
        )
    }

    @Test
    fun deleteTask_borraLogicamenteLasSubtareasActivasConElMismoInstante() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        taskDao.putSubtask(subtaskEntity("s1", taskId = "t1"))
        taskDao.putSubtask(subtaskEntity("s2", taskId = "t1"))
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { later })

        repository.deleteTask("t1")

        listOf("s1", "s2").forEach { id ->
            val row = taskDao.subtasks.getValue(id)
            assertEquals(later.toEpochMilli(), row.deletedAt)
            assertEquals(later.toEpochMilli(), row.updatedAt)
            assertTrue(row.pending)
        }
        assertEquals(later.toEpochMilli(), taskDao.stored.getValue("t1").deletedAt)
    }

    @Test
    fun deleteTask_noTocaSubtareasDeOtraTareaNiLasYaBorradas() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        val other = subtaskEntity("ajena", taskId = "t2")
        val alreadyDeleted = subtaskEntity("vieja", taskId = "t1", deletedAt = now.toEpochMilli())
        taskDao.putSubtask(other)
        taskDao.putSubtask(alreadyDeleted)
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { later })

        repository.deleteTask("t1")

        assertEquals(other, taskDao.subtasks.getValue("ajena"))
        assertEquals(now.toEpochMilli(), taskDao.subtasks.getValue("vieja").deletedAt)
    }

    @Test
    fun deleteTask_tareaInexistente_noEscribeNiTocaSubtareas() = runTest {
        val columnDao = FakeColumnDao()
        val taskDao = FakeTaskDao(columnDao)
        val subtask = subtaskEntity("s1", taskId = "t1")
        taskDao.putSubtask(subtask)
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { later })

        val result = repository.deleteTask("t1")

        assertEquals("TASK_NOT_FOUND", (result as DomainResult.Failure).error.code)
        assertEquals(0, taskDao.transactionalCalls)
        assertEquals(subtask, taskDao.subtasks.getValue("s1"))
    }

    @Test
    fun deleteTask_usaLaOperacionTransaccionalUnica() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { later })
        val writesBefore = taskDao.writeCalls

        repository.deleteTask("t1")

        assertEquals(1, taskDao.transactionalCalls)
        assertEquals(writesBefore, taskDao.writeCalls)
    }

    @Test
    fun deleteTask_deUnaTareaBorrada_fallaConNotFound() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0, updatedAt = now))
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { later })
        repository.deleteTask("t1")
        val afterDelete = taskDao.stored.getValue("t1")
        val writesAfterDelete = taskDao.writeCalls

        val result = repository.deleteTask("t1")

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.NotFound)
        assertEquals("TASK_NOT_FOUND", error.code)
        assertEquals(afterDelete, taskDao.stored.getValue("t1"))
        assertEquals(writesAfterDelete, taskDao.writeCalls)
    }

    @Test
    fun observeTasks_emiteSoloLasActivasOrdenadasPorPosition() = runTest {
        val columnDao = FakeColumnDao().apply {
            put(columnEntity("columna-1"))
            put(columnEntity("columna-borrada", deleted = true))
        }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t2", columnId = "columna-1", position = 1))
        taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        taskDao.put(taskEntity("b", columnId = "columna-1", position = 0))
        taskDao.put(taskEntity("borrada", columnId = "columna-1", position = 0, deletedAt = later))
        taskDao.put(taskEntity("enColumnaBorrada", columnId = "columna-borrada", position = 0))
        taskDao.put(taskEntity("ajena", columnId = "columna-2", position = 0))
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { now })

        val tasks = repository.observeTasks(BOARD_ID).first()

        assertEquals(listOf("b", "t1", "t2"), tasks.map { it.id })
        assertEquals(listOf(0, 0, 1), tasks.map { it.position })
        assertFalse(tasks.any { it.isDeleted })
    }

    private fun columnEntity(id: String, deleted: Boolean = false): ColumnEntity = ColumnEntity(
        id = id,
        boardId = BOARD_ID,
        name = "Por hacer",
        position = 0,
        wipLimit = null,
        isDoneColumn = false,
        updatedAt = now.toEpochMilli(),
        pending = true,
        deletedAt = if (deleted) now.toEpochMilli() else null,
    )

    private fun subtaskEntity(id: String, taskId: String, deletedAt: Long? = null): SubtaskEntity =
        SubtaskEntity(
            id = id,
            taskId = taskId,
            title = "Subtarea $id",
            completed = false,
            position = 0,
            updatedAt = now.toEpochMilli(),
            pending = false,
            deletedAt = deletedAt,
        )

    private fun taskEntity(
        id: String,
        columnId: String,
        position: Int,
        updatedAt: Instant = now,
        deletedAt: Instant? = null,
    ): TaskEntity = TaskEntity(
        id = id,
        columnId = columnId,
        title = "Tarea $id",
        description = null,
        priority = "MEDIUM",
        dueAt = null,
        assigneeId = null,
        position = position,
        updatedAt = updatedAt.toEpochMilli(),
        pending = true,
        deletedAt = deletedAt?.toEpochMilli(),
    )

    private class FakeTaskDao(private val columnDao: FakeColumnDao) : TaskDao() {
        private val rows = MutableStateFlow<Map<String, TaskEntity>>(emptyMap())

        var writeCalls: Int = 0
            private set

        val stored: Map<String, TaskEntity> get() = rows.value

        fun put(task: TaskEntity) {
            rows.value = rows.value + (task.id to task)
        }

        override fun observeByBoard(boardId: String): Flow<List<TaskEntity>> = rows.map { storedRows ->
            val activeColumns = columnDao.activeColumnIds(boardId)
            storedRows.values
                .filter { it.columnId in activeColumns && it.deletedAt == null }
                .sortedWith(compareBy({ it.position }, { it.id }))
        }

        override suspend fun findActiveByColumn(columnId: String): List<TaskEntity> =
            rows.value.values
                .filter { it.columnId == columnId && it.deletedAt == null }
                .sortedWith(compareBy({ it.position }, { it.id }))

        override suspend fun findById(id: String): TaskEntity? = rows.value[id]

        override suspend fun countByColumn(columnId: String): Int =
            rows.value.values.count { it.columnId == columnId }

        val subtasks: MutableMap<String, SubtaskEntity> = mutableMapOf()

        var transactionalCalls: Int = 0
            private set

        private var inTransaction = false

        fun putSubtask(subtask: SubtaskEntity) {
            subtasks[subtask.id] = subtask
        }

        override suspend fun upsert(task: TaskEntity) {
            if (!inTransaction) {
                writeCalls++
            }
            rows.value = rows.value + (task.id to task)
        }

        override suspend fun softDeleteSubtasksOfTask(taskId: String, deletedAt: Long) {
            subtasks.replaceAll { _, row ->
                if (row.taskId == taskId && row.deletedAt == null) {
                    row.copy(deletedAt = deletedAt, updatedAt = deletedAt, pending = true)
                } else {
                    row
                }
            }
        }

        override suspend fun upsertDeletedWithSubtasks(task: TaskEntity, deletedAt: Long) {
            transactionalCalls++
            inTransaction = true
            try {
                super.upsertDeletedWithSubtasks(task, deletedAt)
            } finally {
                inTransaction = false
            }
        }
    }

    private class FakeColumnDao : ColumnDao() {
        private val rows = MutableStateFlow<Map<String, ColumnEntity>>(emptyMap())

        fun put(column: ColumnEntity) {
            rows.value = rows.value + (column.id to column)
        }

        fun activeColumnIds(boardId: String): Set<String> =
            rows.value.values
                .filter { it.boardId == boardId && it.deletedAt == null }
                .map { it.id }
                .toSet()

        override fun observeByBoard(boardId: String): Flow<List<ColumnEntity>> = rows.map { storedRows ->
            storedRows.values.filter { it.boardId == boardId && it.deletedAt == null }
        }

        override suspend fun findActiveByBoard(boardId: String): List<ColumnEntity> =
            rows.value.values.filter { it.boardId == boardId && it.deletedAt == null }

        override suspend fun findById(id: String): ColumnEntity? = rows.value[id]

        override suspend fun upsert(column: ColumnEntity) {
            rows.value = rows.value + (column.id to column)
        }

        override suspend fun upsertAll(columns: List<ColumnEntity>) {
            rows.value = rows.value + columns.associateBy { it.id }
        }

        override suspend fun insertBoard(board: BoardEntity) = Unit
    }

    private companion object {
        const val BOARD_ID = "tablero-1"
    }
}
