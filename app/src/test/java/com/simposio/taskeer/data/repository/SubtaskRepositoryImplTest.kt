package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.SubtaskDao
import com.simposio.taskeer.data.local.SubtaskEntity
import com.simposio.taskeer.data.local.ReminderCandidateRow
import com.simposio.taskeer.data.local.ReminderTaskStateRow
import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.SubtaskDraft
import com.simposio.taskeer.domain.model.SubtaskProgress
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtaskRepositoryImplTest {

    private val now: Instant = Instant.parse("2026-10-21T10:00:00Z")
    private val later: Instant = now.plusSeconds(60)

    @Test
    fun createSubtask_anhadeAlFinalYPersistePendiente() = runTest {
        val fixture = fixture()
        fixture.dao.put(entity("s0", position = 0))
        fixture.dao.put(entity("s1", position = 1))

        val result = fixture.repository.createSubtask("t1", SubtaskDraft("  Nueva  "))

        val created = (result as DomainResult.Success).value
        assertEquals("Nueva", created.title)
        assertEquals(2, created.position)
        assertFalse(created.completed)
        val row = fixture.dao.stored.getValue(created.id)
        assertEquals("t1", row.taskId)
        assertEquals(now.toEpochMilli(), row.updatedAt)
        assertTrue(row.pending)
        assertNull(row.deletedAt)
    }

    @Test
    fun createSubtask_sinTitulo_noEscribe() = runTest {
        val fixture = fixture()

        val result = fixture.repository.createSubtask("t1", SubtaskDraft("   "))

        assertEquals("SUBTASK_TITLE_REQUIRED", (result as DomainResult.Failure).error.code)
        assertEquals(0, fixture.dao.writeCalls)
    }

    @Test
    fun createSubtask_tareaInexistenteOBorrada_devuelveTaskNotFound() = runTest {
        val fixture = fixture(taskDeleted = true)

        val deleted = fixture.repository.createSubtask("t1", SubtaskDraft("A"))
        val missing = fixture.repository.createSubtask("no-existe", SubtaskDraft("A"))

        listOf(deleted, missing).forEach { result ->
            val error = (result as DomainResult.Failure).error
            assertTrue(error is DomainError.NotFound)
            assertEquals("TASK_NOT_FOUND", error.code)
        }
        assertEquals(0, fixture.dao.writeCalls)
    }

    @Test
    fun updateSubtask_cambiaTituloYMarcaPendiente() = runTest {
        val fixture = fixture(clock = later)
        val original = entity("s1", position = 3, completed = true, pending = false)
        fixture.dao.put(original)

        val result = fixture.repository.updateSubtask("s1", SubtaskDraft("  Otro  "))

        assertTrue(result is DomainResult.Success)
        assertEquals(
            original.copy(title = "Otro", updatedAt = later.toEpochMilli(), pending = true),
            fixture.dao.stored.getValue("s1"),
        )
    }

    @Test
    fun updateSubtask_sinTitulo_noEscribe() = runTest {
        val fixture = fixture()
        fixture.dao.put(entity("s1"))
        val writesBefore = fixture.dao.writeCalls

        val result = fixture.repository.updateSubtask("s1", SubtaskDraft(" "))

        assertEquals("SUBTASK_TITLE_REQUIRED", (result as DomainResult.Failure).error.code)
        assertEquals(writesBefore, fixture.dao.writeCalls)
    }

    @Test
    fun setSubtaskCompleted_guardaElNuevoEstado() = runTest {
        val fixture = fixture(clock = later)
        fixture.dao.put(entity("s1", pending = false))

        val result = fixture.repository.setSubtaskCompleted("s1", true)

        assertTrue((result as DomainResult.Success).value.completed)
        val row = fixture.dao.stored.getValue("s1")
        assertTrue(row.completed)
        assertTrue(row.pending)
        assertEquals(later.toEpochMilli(), row.updatedAt)
        assertEquals("Subtarea s1", row.title)
    }

    @Test
    fun observeSubtasks_soloActivasDeLaTareaOrdenadas() = runTest {
        val fixture = fixture()
        fixture.dao.put(entity("b", position = 0))
        fixture.dao.put(entity("a", position = 0))
        fixture.dao.put(entity("c", position = 1))
        fixture.dao.put(entity("borrada", position = 0, deletedAt = later.toEpochMilli()))
        fixture.dao.put(entity("ajena", taskId = "t2", position = 0))

        val items = fixture.repository.observeSubtasks("t1").first()

        assertEquals(listOf("a", "b", "c"), items.map { it.id })
    }

    @Test
    fun deleteSubtask_hazBorradoLogicoYLaDejaDeListar() = runTest {
        val fixture = fixture(clock = later)
        fixture.dao.put(entity("s1"))

        val result = fixture.repository.deleteSubtask("s1")

        assertTrue(result is DomainResult.Success)
        val row = fixture.dao.stored.getValue("s1")
        assertEquals(later.toEpochMilli(), row.deletedAt)
        assertEquals(later.toEpochMilli(), row.updatedAt)
        assertTrue(row.pending)
        assertTrue(fixture.repository.observeSubtasks("t1").first().isEmpty())
    }

    @Test
    fun operacionesSobreSubtareaInexistenteOBorrada_devuelvenSubtaskNotFound() = runTest {
        val fixture = fixture()
        fixture.dao.put(entity("vieja", deletedAt = now.toEpochMilli()))
        val writesBefore = fixture.dao.writeCalls

        listOf("vieja", "no-existe").forEach { id ->
            val results = listOf(
                fixture.repository.updateSubtask(id, SubtaskDraft("A")),
                fixture.repository.setSubtaskCompleted(id, true),
                fixture.repository.deleteSubtask(id),
            )
            results.forEach { result ->
                val error = (result as DomainResult.Failure).error
                assertTrue(error is DomainError.NotFound)
                assertEquals("SUBTASK_NOT_FOUND", error.code)
            }
        }
        assertEquals(writesBefore, fixture.dao.writeCalls)
    }

    @Test
    fun createSubtask_conAsignadoYFecha_losPersisteYRecortaElAsignado() = runTest {
        val fixture = fixture()
        val due = now.plusSeconds(3600)

        val result = fixture.repository.createSubtask("t1", SubtaskDraft("A", "  local-user  ", due))

        val row = fixture.dao.stored.getValue((result as DomainResult.Success).value.id)
        assertEquals("local-user", row.assigneeId)
        assertEquals(due.toEpochMilli(), row.dueAt)
        assertEquals(now.toEpochMilli(), row.createdAt)
    }

    @Test
    fun updateSubtask_sinAsignadoNiFecha_losQuitaYConservaCreatedAt() = runTest {
        val fixture = fixture(clock = later)
        fixture.dao.put(entity("s1").copy(assigneeId = "local-user", dueAt = 5L, createdAt = 7L))

        fixture.repository.updateSubtask("s1", SubtaskDraft("A"))

        val row = fixture.dao.stored.getValue("s1")
        assertNull(row.assigneeId)
        assertNull(row.dueAt)
        assertEquals(7L, row.createdAt)
    }

    @Test
    fun setSubtaskCompleted_conservaAsignadoYFecha() = runTest {
        val fixture = fixture(clock = later)
        fixture.dao.put(entity("s1").copy(assigneeId = "local-user", dueAt = 5L))

        fixture.repository.setSubtaskCompleted("s1", true)

        val row = fixture.dao.stored.getValue("s1")
        assertEquals("local-user", row.assigneeId)
        assertEquals(5L, row.dueAt)
    }

    @Test
    fun createSubtask_tituloDe201_noEscribeYNombraElLimite() = runTest {
        val fixture = fixture()

        val result = fixture.repository.createSubtask("t1", SubtaskDraft("x".repeat(201)))

        val error = (result as DomainResult.Failure).error
        assertEquals("SUBTASK_TITLE_TOO_LONG", error.code)
        assertTrue(error.message.contains("200"))
        assertEquals(0, fixture.dao.writeCalls)
    }

    @Test
    fun observeSubtaskProgress_cuentaTotalYCompletadasSoloActivas() = runTest {
        val fixture = fixture()
        fixture.dao.put(entity("a", completed = true))
        fixture.dao.put(entity("b"))
        fixture.dao.put(entity("c"))
        fixture.dao.put(entity("borrada", completed = true, deletedAt = later.toEpochMilli()))
        fixture.dao.put(entity("ajena", taskId = "t2", completed = true))

        assertEquals(SubtaskProgress(3, 1), fixture.repository.observeSubtaskProgress("t1").first())
    }

    @Test
    fun observeSubtaskProgress_alBorrarUnaCompletada_baja() = runTest {
        val fixture = fixture()
        fixture.dao.put(entity("a", completed = true))
        fixture.dao.put(entity("b"))
        fixture.dao.put(entity("c"))

        fixture.repository.deleteSubtask("a")

        assertEquals(SubtaskProgress(2, 0), fixture.repository.observeSubtaskProgress("t1").first())
    }

    @Test
    fun observeSubtaskProgress_sinSubtareasOTareaInexistente_esCeroCero() = runTest {
        val fixture = fixture()

        assertEquals(SubtaskProgress(0, 0), fixture.repository.observeSubtaskProgress("t1").first())
        assertEquals(SubtaskProgress(0, 0), fixture.repository.observeSubtaskProgress("no-existe").first())
    }

    private class Fixture(val dao: FakeSubtaskDao, val repository: SubtaskRepositoryImpl)

    private fun fixture(clock: Instant = now, taskDeleted: Boolean = false): Fixture {
        val dao = FakeSubtaskDao()
        val taskDao = FakeTaskDao(
            TaskEntity(
                id = "t1",
                columnId = "c1",
                title = "Tarea",
                description = null,
                priority = "MEDIUM",
                dueAt = null,
                assigneeId = null,
                position = 0,
                createdAt = 0L,
                updatedAt = now.toEpochMilli(),
                pending = false,
                deletedAt = if (taskDeleted) now.toEpochMilli() else null,
            ),
        )
        return Fixture(dao, SubtaskRepositoryImpl(dao, taskDao, now = { clock }))
    }

    private fun entity(
        id: String,
        taskId: String = "t1",
        position: Int = 0,
        completed: Boolean = false,
        pending: Boolean = true,
        deletedAt: Long? = null,
    ): SubtaskEntity = SubtaskEntity(
        id = id,
        taskId = taskId,
        title = "Subtarea $id",
        completed = completed,
        position = position,
        assigneeId = null,
        dueAt = null,
        createdAt = 0L,
        updatedAt = now.toEpochMilli(),
        pending = pending,
        deletedAt = deletedAt,
    )

    private class FakeSubtaskDao : SubtaskDao() {
        private val rows = MutableStateFlow<Map<String, SubtaskEntity>>(emptyMap())

        var writeCalls: Int = 0
            private set

        val stored: Map<String, SubtaskEntity> get() = rows.value

        fun put(subtask: SubtaskEntity) {
            rows.value = rows.value + (subtask.id to subtask)
        }

        override fun observeByTask(taskId: String): Flow<List<SubtaskEntity>> =
            rows.map { activeOf(it.values, taskId) }

        override suspend fun findActiveByTask(taskId: String): List<SubtaskEntity> =
            activeOf(rows.value.values, taskId)

        override fun observeTotalCount(taskId: String): Flow<Int> =
            rows.map { activeOf(it.values, taskId).size }

        override fun observeCompletedCount(taskId: String): Flow<Int> =
            rows.map { activeOf(it.values, taskId).count { subtask -> subtask.completed } }

        override suspend fun findById(id: String): SubtaskEntity? = rows.value[id]

        override suspend fun upsert(subtask: SubtaskEntity) {
            writeCalls++
            rows.value = rows.value + (subtask.id to subtask)
        }

        private fun activeOf(all: Collection<SubtaskEntity>, taskId: String): List<SubtaskEntity> =
            all.filter { it.taskId == taskId && it.deletedAt == null }
                .sortedWith(compareBy({ it.position }, { it.id }))
    }

    private class FakeTaskDao(private val task: TaskEntity) : TaskDao() {
        override fun observeByBoard(boardId: String): Flow<List<TaskEntity>> = flowOf(emptyList())

        override suspend fun findActiveByColumn(columnId: String): List<TaskEntity> = emptyList()

        override suspend fun findById(id: String): TaskEntity? = task.takeIf { it.id == id }

        override suspend fun countByColumn(columnId: String): Int = 0

        override suspend fun upsert(task: TaskEntity) = Unit

        override suspend fun upsertAll(tasks: List<TaskEntity>) = Unit

        override suspend fun softDeleteSubtasksOfTask(taskId: String, deletedAt: Long) = Unit

        override fun observeReminderCandidates(): Flow<List<ReminderCandidateRow>> = emptyFlow()

        override suspend fun countActive(): Int = 0

        override suspend fun findReminderState(taskId: String): ReminderTaskStateRow? = null

        override suspend fun dismissRemindersOfTask(taskId: String, now: Long): Int = 0
    }
}
