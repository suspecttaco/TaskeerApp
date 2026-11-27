package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.BoardEntity
import com.simposio.taskeer.data.local.ColumnCountRow
import com.simposio.taskeer.data.local.ColumnDao
import com.simposio.taskeer.data.local.ColumnEntity
import com.simposio.taskeer.data.local.ListCountRow
import com.simposio.taskeer.data.local.MyDayEntryEntity
import com.simposio.taskeer.data.local.ReminderCandidateRow
import com.simposio.taskeer.data.local.ReminderEntity
import com.simposio.taskeer.data.local.ReminderTaskStateRow
import com.simposio.taskeer.data.local.ReminderDao
import com.simposio.taskeer.data.local.SubtaskDao
import com.simposio.taskeer.data.local.SubtaskEntity
import com.simposio.taskeer.data.local.SearchTaskRow
import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.data.local.toReminder
import com.simposio.taskeer.data.local.toTask
import com.simposio.taskeer.data.local.toTaskList
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.list.SEEDED_TASK_LIST_ID
import com.simposio.taskeer.domain.list.defaultListOf
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.model.ReminderStatus
import com.simposio.taskeer.domain.model.SubtaskDraft
import com.simposio.taskeer.domain.model.TaskCreation
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.task.successorIdFor
import com.simposio.taskeer.testing.FakeTaskListDao
import com.simposio.taskeer.testing.FakeTaskStore
import com.simposio.taskeer.testing.listEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { now })

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
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { now })

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
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { now })

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
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { now })

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
    fun createTask_sinAsignado_persisteLocalUser() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { now })

        val created = (repository.createTask("columna-1", TaskDraft(title = "Tarea")) as DomainResult.Success).value

        assertEquals(LocalUser.ID, taskDao.stored.getValue(created.id).assigneeId)
    }

    @Test
    fun updateTask_sinAsignado_persisteLocalUser() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0, updatedAt = now))
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { later })

        repository.updateTask("t1", TaskDraft(title = "Tarea", assigneeId = null))

        assertEquals(LocalUser.ID, taskDao.stored.getValue("t1").assigneeId)
    }

    @Test
    fun createTask_persisteElDueAtEnUtcSinDesplazarLaZona() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { now })
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
    fun createTaskWithExtras_persisteTareaSubtareasYRecordatorioEnUnaLlamada() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t0", columnId = "columna-1", position = 0))
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { now })
        val dueAt = now.plusSeconds(7200)

        val result = repository.createTaskWithExtras(
            "columna-1",
            TaskCreation(
                task = TaskDraft(title = "Informe"),
                subtasks = listOf(
                    SubtaskDraft("Datos", assigneeId = LocalUser.ID, dueAt = dueAt),
                    SubtaskDraft("Borrador"),
                ),
                remindAt = now.plusSeconds(3600),
            ),
        )

        val created = (result as DomainResult.Success).value
        assertEquals(1, taskDao.extrasCalls)
        assertEquals(1, created.position)
        assertTrue(taskDao.stored.getValue(created.id).pending)
        val children = taskDao.subtasks.values.sortedBy { it.position }
        assertEquals(listOf("Datos", "Borrador"), children.map { it.title })
        assertEquals(listOf(0, 1), children.map { it.position })
        assertEquals(listOf(created.id, created.id), children.map { it.taskId })
        assertEquals(listOf(false, false), children.map { it.completed })
        assertEquals(listOf(LocalUser.ID, null), children.map { it.assigneeId })
        assertEquals(dueAt.toEpochMilli(), children[0].dueAt)
        assertTrue(children.all { it.pending && it.deletedAt == null })
        assertEquals(listOf(created.id), taskDao.reminders.values.map { it.taskId })
    }

    @Test
    fun createTaskWithExtras_conTituloInvalido_noEscribeNada() = runTest {
        val (repository, taskDao) = extrasRepository()

        val error = failureOf(
            repository.createTaskWithExtras(
                "columna-1",
                TaskCreation(TaskDraft(title = " "), listOf(SubtaskDraft("Datos")), now.plusSeconds(60)),
            ),
        )

        assertEquals("TASK_TITLE_REQUIRED", error.code)
        assertNothingWritten(taskDao)
    }

    @Test
    fun createTaskWithExtras_conSubtareaInvalida_noEscribeNada() = runTest {
        val (repository, taskDao) = extrasRepository()

        val error = failureOf(
            repository.createTaskWithExtras(
                "columna-1",
                TaskCreation(
                    TaskDraft(title = "Informe"),
                    listOf(SubtaskDraft("Bien"), SubtaskDraft("x".repeat(201))),
                    now.plusSeconds(60),
                ),
            ),
        )

        assertEquals("SUBTASK_TITLE_TOO_LONG", error.code)
        assertNothingWritten(taskDao)
    }

    @Test
    fun createTaskWithExtras_conRecordatorioPasado_noEscribeNada() = runTest {
        val (repository, taskDao) = extrasRepository()

        val error = failureOf(
            repository.createTaskWithExtras(
                "columna-1",
                TaskCreation(TaskDraft(title = "Informe"), listOf(SubtaskDraft("Datos")), now),
            ),
        )

        assertEquals("VALIDATION_ERROR", error.code)
        assertNothingWritten(taskDao)
    }

    @Test
    fun createTaskWithExtras_conColumnaLlena_noEscribeNada() = runTest {
        val (repository, taskDao) = extrasRepository(wipLimit = 1)
        taskDao.put(taskEntity("t0", columnId = "columna-1", position = 0))

        val error = failureOf(
            repository.createTaskWithExtras(
                "columna-1",
                TaskCreation(TaskDraft(title = "Informe"), listOf(SubtaskDraft("Datos")), now.plusSeconds(60)),
            ),
        )

        assertEquals("COLUMN_WIP_LIMIT_REACHED", error.code)
        assertEquals(0, taskDao.extrasCalls)
        assertEquals(setOf("t0"), taskDao.stored.keys)
        assertTrue(taskDao.subtasks.isEmpty())
        assertTrue(taskDao.reminders.isEmpty())
    }

    @Test
    fun createTaskWithExtras_conColumnaInexistente_devuelveNotFoundSinEscribir() = runTest {
        val columnDao = FakeColumnDao()
        val taskDao = FakeTaskDao(columnDao)
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { now })

        val error = failureOf(
            repository.createTaskWithExtras(
                "columna-x",
                TaskCreation(TaskDraft(title = "Informe"), listOf(SubtaskDraft("Datos")), now.plusSeconds(60)),
            ),
        )

        assertTrue(error is DomainError.NotFound)
        assertEquals("COLUMN_NOT_FOUND", error.code)
        assertNothingWritten(taskDao)
    }

    @Test
    fun createTaskWithExtras_recordatorioEscritoMapeaAPendingConSuRemindAt() = runTest {
        val (repository, taskDao) = extrasRepository()
        val remindAt = now.plusSeconds(3600)

        val created = (
            repository.createTaskWithExtras(
                "columna-1",
                TaskCreation(TaskDraft(title = "Informe"), remindAt = remindAt),
            ) as DomainResult.Success
            ).value

        val reminder = taskDao.reminders.values.single().toReminder()
        assertEquals(ReminderStatus.PENDING, reminder.status)
        assertEquals(remindAt, reminder.remindAt)
        assertEquals(created.id, reminder.taskId)
        assertNull(reminder.sentAt)
    }

    @Test
    fun createTaskWithExtras_sinExtras_seComportaComoCreateTask() = runTest {
        val (repository, taskDao) = extrasRepository()

        val created = (
            repository.createTaskWithExtras("columna-1", TaskCreation(TaskDraft(title = "  Sola  ")))
                as DomainResult.Success
            ).value

        assertEquals("Sola", created.title)
        assertEquals(0, created.position)
        assertEquals(LocalUser.ID, created.assigneeId)
        assertEquals(listOf(created.id), taskDao.stored.keys.toList())
        assertTrue(taskDao.subtasks.isEmpty())
        assertTrue(taskDao.reminders.isEmpty())
    }

    private fun extrasRepository(wipLimit: Int? = null): Pair<TaskRepositoryImpl, FakeTaskDao> {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1", wipLimit = wipLimit)) }
        val taskDao = FakeTaskDao(columnDao)
        return TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { now }) to taskDao
    }

    private fun failureOf(result: DomainResult<*>): DomainError = (result as DomainResult.Failure).error

    private fun assertNothingWritten(taskDao: FakeTaskDao) {
        assertEquals(0, taskDao.extrasCalls)
        assertEquals(0, taskDao.writeCalls)
        assertTrue(taskDao.stored.isEmpty())
        assertTrue(taskDao.subtasks.isEmpty())
        assertTrue(taskDao.reminders.isEmpty())
    }

    @Test
    fun updateTask_persisteLosCamposYConservaElResto() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t1", columnId = "columna-1", position = 3, updatedAt = now))
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { later })
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
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { later })

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
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { later })

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
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { now })

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
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { later })

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
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { later })

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
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { later })

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
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { later })

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
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { later })
        val writesBefore = taskDao.writeCalls

        repository.deleteTask("t1")

        assertEquals(1, taskDao.transactionalCalls)
        assertEquals(writesBefore, taskDao.writeCalls)
    }

    private class RestoreFixture(val taskDao: FakeTaskDao, val columnDao: FakeColumnDao) {
        fun repository(at: Instant): TaskRepositoryImpl = TaskRepositoryImpl(
            taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId,
            now = { at },
        )
    }

    private fun restoreFixture(wipLimit: Int? = null): RestoreFixture {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1", wipLimit = wipLimit)) }
        return RestoreFixture(FakeTaskDao(columnDao), columnDao)
    }

    private suspend fun RestoreFixture.deleteAt(at: Instant, vararg ids: String) {
        ids.forEach { repository(at).deleteTask(it) }
    }

    @Test
    fun restoreTasks_reactivaLaTareaConSusSubtareasDelMismoBorrado() = runTest {
        val fixture = restoreFixture()
        fixture.taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        fixture.taskDao.putSubtask(subtaskEntity("s1", taskId = "t1"))
        fixture.deleteAt(now, "t1")

        val result = fixture.repository(later).restoreTasks(listOf("t1"))

        assertTrue(result is DomainResult.Success)
        assertNull(fixture.taskDao.stored.getValue("t1").deletedAt)
        val subtask = fixture.taskDao.subtasks.getValue("s1")
        assertNull(subtask.deletedAt)
        assertEquals(later.toEpochMilli(), subtask.updatedAt)
        assertTrue(subtask.pending)
    }

    @Test
    fun restoreTasks_noReactivaSubtareasBorradasAntes() = runTest {
        val fixture = restoreFixture()
        val earlier = now.minusSeconds(3600).toEpochMilli()
        fixture.taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        fixture.taskDao.putSubtask(subtaskEntity("s-vieja", taskId = "t1", deletedAt = earlier))
        fixture.deleteAt(now, "t1")

        fixture.repository(later).restoreTasks(listOf("t1"))

        assertEquals(earlier, fixture.taskDao.subtasks.getValue("s-vieja").deletedAt)
    }

    @Test
    fun restoreTasks_reactivaElRecordatorioFuturo() = runTest {
        val fixture = restoreFixture()
        fixture.taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        fixture.taskDao.putReminder(reminderEntity("r1", taskId = "t1", status = "PENDING"))
        fixture.deleteAt(now, "t1")
        val restoredAt = now.plusSeconds(10)

        fixture.repository(restoredAt).restoreTasks(listOf("t1"))

        val reminder = fixture.taskDao.reminders.getValue("r1")
        assertEquals("PENDING", reminder.status)
        assertEquals(restoredAt.toEpochMilli(), reminder.updatedAt)
        assertTrue(reminder.pending)
    }

    @Test
    fun restoreTasks_noReactivaElRecordatorioVencido() = runTest {
        val fixture = restoreFixture()
        fixture.taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        fixture.taskDao.putReminder(reminderEntity("r1", taskId = "t1", status = "PENDING"))
        fixture.deleteAt(now, "t1")

        fixture.repository(later.plusSeconds(3600)).restoreTasks(listOf("t1"))

        assertEquals("DISMISSED", fixture.taskDao.reminders.getValue("r1").status)
        assertNull(fixture.taskDao.stored.getValue("t1").deletedAt)
    }

    @Test
    fun restoreTasks_conservaLaPosicionSiEstaLibre() = runTest {
        val fixture = restoreFixture()
        fixture.taskDao.put(taskEntity("t0", columnId = "columna-1", position = 0))
        fixture.taskDao.put(taskEntity("t1", columnId = "columna-1", position = 1))
        fixture.deleteAt(now, "t1")

        fixture.repository(later).restoreTasks(listOf("t1"))

        assertEquals(1, fixture.taskDao.stored.getValue("t1").position)
    }

    @Test
    fun restoreTasks_posicionOcupada_vaAlFinal() = runTest {
        val fixture = restoreFixture()
        fixture.taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        fixture.deleteAt(now, "t1")
        fixture.taskDao.put(taskEntity("t2", columnId = "columna-1", position = 0))
        fixture.taskDao.put(taskEntity("t3", columnId = "columna-1", position = 1))

        fixture.repository(later).restoreTasks(listOf("t1"))

        assertEquals(2, fixture.taskDao.stored.getValue("t1").position)
    }

    @Test
    fun restoreTasks_enListaBorrada_vaALaListaPorDefecto() = runTest {
        val fixture = restoreFixture()
        fixture.taskDao.listDao.put(listEntity("lista-x", "Vieja", position = 1, deletedAt = 5L))
        fixture.taskDao.put(taskEntity("t1", columnId = null, position = 0, listId = "lista-x"))
        fixture.taskDao.put(taskEntity("otra", columnId = null, position = 0))
        fixture.deleteAt(now, "t1")

        fixture.repository(later).restoreTasks(listOf("t1"))

        val restored = fixture.taskDao.stored.getValue("t1")
        assertEquals(SEEDED_TASK_LIST_ID, restored.listId)
        assertEquals(1, restored.position)
        assertNull(restored.columnId)
    }

    @Test
    fun restoreTasks_enListaActiva_reapareceEnObserveList() = runTest {
        val fixture = restoreFixture()
        fixture.taskDao.put(taskEntity("t1", columnId = null, position = 0))
        fixture.taskDao.put(taskEntity("t2", columnId = null, position = 1))
        fixture.taskDao.putSubtask(subtaskEntity("s1", taskId = "t1"))
        fixture.deleteAt(now, "t1")
        assertEquals(listOf("t2"), fixture.repository(now).observeList(SEEDED_TASK_LIST_ID).first().map { it.id })

        val result = fixture.repository(later).restoreTasks(listOf("t1"))

        assertTrue(result is DomainResult.Success)
        val visible = fixture.repository(later).observeList(SEEDED_TASK_LIST_ID).first()
        assertEquals(setOf("t1", "t2"), visible.map { it.id }.toSet())
        assertEquals(SEEDED_TASK_LIST_ID, fixture.taskDao.stored.getValue("t1").listId)
        assertNull(fixture.taskDao.subtasks.getValue("s1").deletedAt)
    }

    @Test
    fun restoreTasks_noDuplicaMiDiaNiSucesora() = runTest {
        val fixture = restoreFixture()
        fixture.taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        fixture.taskDao.put(taskEntity("t1-sucesora", columnId = "columna-1", position = 1))
        fixture.taskDao.myDayEntries["t1"] = MyDayEntryEntity("t1", addedAt = 1L, updatedAt = 1L, pending = false, deletedAt = null)
        fixture.deleteAt(now, "t1")
        val idsBefore = fixture.taskDao.stored.keys
        val myDayBefore = fixture.taskDao.myDayEntries.toMap()

        fixture.repository(later).restoreTasks(listOf("t1"))

        assertEquals(idsBefore, fixture.taskDao.stored.keys)
        assertEquals(myDayBefore, fixture.taskDao.myDayEntries)
    }

    @Test
    fun restoreTasks_marcaPendingYUpdatedAt() = runTest {
        val fixture = restoreFixture()
        fixture.taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        fixture.deleteAt(now, "t1")

        fixture.repository(later).restoreTasks(listOf("t1"))

        val row = fixture.taskDao.stored.getValue("t1")
        assertEquals(later.toEpochMilli(), row.updatedAt)
        assertTrue(row.pending)
    }

    @Test
    fun restoreTasks_tareaActiva_fallaSinEscribir() = runTest {
        val fixture = restoreFixture()
        fixture.taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        val writesBefore = fixture.taskDao.writeCalls

        val error = (fixture.repository(later).restoreTasks(listOf("t1")) as DomainResult.Failure).error

        assertTrue(error is DomainError.Conflict)
        assertEquals("TASK_NOT_DELETED", error.code)
        assertEquals(0, fixture.taskDao.restoreCalls)
        assertEquals(writesBefore, fixture.taskDao.writeCalls)
    }

    @Test
    fun restoreTasks_inexistente_falla() = runTest {
        val fixture = restoreFixture()

        val error = (fixture.repository(later).restoreTasks(listOf("nada")) as DomainResult.Failure).error

        assertTrue(error is DomainError.NotFound)
        assertEquals("TASK_NOT_FOUND", error.code)
        assertEquals(0, fixture.taskDao.restoreCalls)
    }

    @Test
    fun restoreTasks_columnaBorrada_fallaSinEscribir() = runTest {
        val fixture = restoreFixture()
        fixture.taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        fixture.deleteAt(now, "t1")
        fixture.columnDao.put(columnEntity("columna-1", deleted = true))

        val error = (fixture.repository(later).restoreTasks(listOf("t1")) as DomainResult.Failure).error

        assertEquals("COLUMN_NOT_FOUND", error.code)
        assertEquals(0, fixture.taskDao.restoreCalls)
        assertEquals(now.toEpochMilli(), fixture.taskDao.stored.getValue("t1").deletedAt)
    }

    @Test
    fun restoreTasks_columnaLlena_fallaSinEscribir() = runTest {
        val fixture = restoreFixture(wipLimit = 1)
        fixture.taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        fixture.deleteAt(now, "t1")
        fixture.taskDao.put(taskEntity("t2", columnId = "columna-1", position = 0))

        val error = (fixture.repository(later).restoreTasks(listOf("t1")) as DomainResult.Failure).error

        assertTrue(error is DomainError.Conflict)
        assertEquals("COLUMN_WIP_LIMIT_REACHED", error.code)
        assertEquals(0, fixture.taskDao.restoreCalls)
        assertEquals(now.toEpochMilli(), fixture.taskDao.stored.getValue("t1").deletedAt)
    }

    @Test
    fun restoreTasks_esTransaccional() = runTest {
        val fixture = restoreFixture()
        fixture.taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        fixture.deleteAt(now, "t1")

        val result = fixture.repository(later).restoreTasks(listOf("t1", "no-existe"))

        assertTrue(result is DomainResult.Failure)
        assertEquals(now.toEpochMilli(), fixture.taskDao.stored.getValue("t1").deletedAt)
        assertEquals(0, fixture.taskDao.restoreCalls)
    }

    @Test
    fun restoreTasks_lote_restauraTodasEnUnaTransaccion() = runTest {
        val fixture = restoreFixture()
        fixture.taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        fixture.taskDao.put(taskEntity("t2", columnId = "columna-1", position = 1))
        fixture.deleteAt(now, "t1", "t2")

        val result = fixture.repository(later).restoreTasks(listOf("t1", "t2"))

        assertTrue(result is DomainResult.Success)
        assertEquals(1, fixture.taskDao.restoreCalls)
        assertEquals(listOf(null, null), listOf("t1", "t2").map { fixture.taskDao.stored.getValue(it).deletedAt })
        assertEquals(listOf(0, 1), listOf("t1", "t2").map { fixture.taskDao.stored.getValue(it).position })
    }

    @Test
    fun restoreTasks_lote_unaFallaYNoRestauraNinguna() = runTest {
        val fixture = restoreFixture()
        fixture.taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        fixture.taskDao.put(taskEntity("t2", columnId = "columna-1", position = 1))
        fixture.deleteAt(now, "t1")

        val result = fixture.repository(later).restoreTasks(listOf("t1", "t2"))

        assertEquals("TASK_NOT_DELETED", (result as DomainResult.Failure).error.code)
        assertEquals(now.toEpochMilli(), fixture.taskDao.stored.getValue("t1").deletedAt)
        assertEquals(0, fixture.taskDao.restoreCalls)
    }

    @Test
    fun restoreTasks_lote_columnaConLimiteSumaElLote() = runTest {
        val fixture = restoreFixture(wipLimit = 2)
        fixture.taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        fixture.taskDao.put(taskEntity("t2", columnId = "columna-1", position = 1))
        fixture.deleteAt(now, "t1", "t2")
        fixture.taskDao.put(taskEntity("t3", columnId = "columna-1", position = 0))

        val result = fixture.repository(later).restoreTasks(listOf("t1", "t2"))

        assertEquals("COLUMN_WIP_LIMIT_REACHED", (result as DomainResult.Failure).error.code)
        assertEquals(listOf(now.toEpochMilli(), now.toEpochMilli()), listOf("t1", "t2").map { fixture.taskDao.stored.getValue(it).deletedAt })
        val fits = fixture.repository(later).restoreTasks(listOf("t1"))
        assertTrue(fits is DomainResult.Success)
    }

    @Test
    fun restoreTasks_lote_noTocaLasBorradasAntes() = runTest {
        val fixture = restoreFixture()
        fixture.taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        fixture.taskDao.put(taskEntity("vieja", columnId = "columna-1", position = 1, deletedAt = now.minusSeconds(3600)))
        fixture.deleteAt(now, "t1")

        fixture.repository(later).restoreTasks(listOf("t1"))

        assertEquals(now.minusSeconds(3600).toEpochMilli(), fixture.taskDao.stored.getValue("vieja").deletedAt)
    }

    private fun completedRepository(vararg ids: String): Pair<TaskRepositoryImpl, FakeTaskDao> {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        ids.forEachIndexed { index, id ->
            taskDao.put(taskEntity(id, columnId = "columna-1", position = index, completedAt = now))
        }
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { later })
        return repository to taskDao
    }

    @Test
    fun deleteCompleted_marcaDeletedAtYPendingEnTodasLasCompletadas() = runTest {
        val (repository, taskDao) = completedRepository("c1", "c2")

        repository.deleteCompleted()

        listOf("c1", "c2").forEach { id ->
            val row = taskDao.stored.getValue(id)
            assertEquals(later.toEpochMilli(), row.deletedAt)
            assertEquals(later.toEpochMilli(), row.updatedAt)
            assertTrue(row.pending)
        }
        assertEquals("las filas no se borran físicamente", 2, taskDao.stored.size)
    }

    @Test
    fun deleteCompleted_noTocaLasPendientesNiLasYaBorradas() = runTest {
        val (repository, taskDao) = completedRepository("c1")
        taskDao.put(taskEntity("pendiente", columnId = "columna-1", position = 5))
        taskDao.put(taskEntity("vieja", columnId = "columna-1", position = 6, completedAt = now, deletedAt = now))

        repository.deleteCompleted()

        assertEquals(null, taskDao.stored.getValue("pendiente").deletedAt)
        assertEquals(now.toEpochMilli(), taskDao.stored.getValue("vieja").deletedAt)
    }

    @Test
    fun deleteCompleted_aplicaLaCascadaDeSubtareas() = runTest {
        val (repository, taskDao) = completedRepository("c1")
        taskDao.putSubtask(subtaskEntity("s1", taskId = "c1"))

        repository.deleteCompleted()

        assertEquals(later.toEpochMilli(), taskDao.subtasks.getValue("s1").deletedAt)
    }

    @Test
    fun deleteCompleted_devuelveLosIdsBorrados() = runTest {
        val (repository, _) = completedRepository("c1", "c2", "c3")

        val result = repository.deleteCompleted()

        assertEquals(listOf("c1", "c2", "c3"), (result as DomainResult.Success).value)
    }

    @Test
    fun deleteCompleted_sinCompletadas_devuelveListaVacia() = runTest {
        val (repository, _) = completedRepository()

        val result = repository.deleteCompleted()

        assertEquals(emptyList<String>(), (result as DomainResult.Success).value)
    }

    @Test
    fun observeCompletedCount_cuentaSoloCompletadasNoBorradas() = runTest {
        val (repository, taskDao) = completedRepository("c1", "c2")
        taskDao.put(taskEntity("pendiente", columnId = "columna-1", position = 5))
        taskDao.put(taskEntity("vieja", columnId = "columna-1", position = 6, completedAt = now, deletedAt = now))

        assertEquals(2, repository.observeCompletedCount().first())
    }

    @Test
    fun deleteTask_descartaLosRecordatoriosPendientesDeLaTareaEnLaMismaTransaccion() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        taskDao.putReminder(reminderEntity("r1", taskId = "t1", status = "PENDING"))
        taskDao.putReminder(reminderEntity("r2", taskId = "t1", status = "SENT"))
        taskDao.putReminder(reminderEntity("r3", taskId = "t2", status = "PENDING"))
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { later })

        repository.deleteTask("t1")

        assertEquals("DISMISSED", taskDao.reminders.getValue("r1").status)
        assertEquals(later.toEpochMilli(), taskDao.reminders.getValue("r1").updatedAt)
        assertEquals("SENT", taskDao.reminders.getValue("r2").status)
        assertEquals("PENDING", taskDao.reminders.getValue("r3").status)
        assertEquals(1, taskDao.transactionalCalls)
    }

    @Test
    fun deleteTask_tareaInexistente_noTocaRecordatorios() = runTest {
        val columnDao = FakeColumnDao()
        val taskDao = FakeTaskDao(columnDao)
        taskDao.putReminder(reminderEntity("r1", taskId = "t1", status = "PENDING"))
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { later })

        repository.deleteTask("t1")

        assertEquals("PENDING", taskDao.reminders.getValue("r1").status)
    }

    @Test
    fun reminderCandidates_excluyeSinFechaBorradasYCompletadas() = runTest {
        val repository = repositoryWithCandidateTasks()

        val ids = repository.reminderCandidates().map { it.taskId }

        assertEquals(listOf("con-fecha"), ids)
    }

    @Test
    fun reminderCandidates_devuelveTituloYFechaLimiteEnUtc() = runTest {
        val repository = repositoryWithCandidateTasks()

        val candidate = repository.reminderCandidates().single()

        assertEquals("Tarea con-fecha", candidate.title)
        assertEquals(DUE, candidate.dueAt)
    }

    @Test
    fun reminderCandidates_excluyeTareasCompletadas() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao).apply {
            put(taskEntity("abierta", columnId = "columna-1", position = 0, dueAt = DUE))
            put(taskEntity("hecha", columnId = "columna-1", position = 1, dueAt = DUE, completedAt = now))
            put(taskEntity("bandeja-hecha", columnId = null, position = 0, dueAt = DUE, completedAt = now))
        }
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { later })

        assertEquals(listOf("abierta"), repository.reminderCandidates().map { it.taskId })
        assertEquals(1, repository.activeTaskCount())

        repository.setCompleted("hecha", false)

        assertEquals(listOf("abierta", "hecha"), repository.reminderCandidates().map { it.taskId }.sorted())
    }

    @Test
    fun counts_incluyenTareasCompletadas() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao).apply {
            put(taskEntity("abierta", columnId = "columna-1", position = 0))
            put(taskEntity("hecha", columnId = "columna-1", position = 1, completedAt = now))
        }
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { later })

        assertEquals(mapOf("columna-1" to 2), repository.observeActiveCountsByColumn().first())
    }

    @Test
    fun reminderCandidates_reapareceAlSacarLaTareaDeLaColumnaCompletada() = runTest {
        val repository = repositoryWithCandidateTasks()

        repository.moveTask("completada", "columna-1", 0)

        assertEquals(
            listOf("completada", "con-fecha"),
            repository.reminderCandidates().map { it.taskId }.sorted(),
        )
    }

    @Test
    fun observeReminderCandidates_reemiteAlEditarLaFechaMoverYBorrar() = runTest {
        val repository = repositoryWithCandidateTasks()
        val emissions = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeReminderCandidates().collect { rows -> emissions += rows.map { it.taskId } }
        }

        repository.updateTask("sin-fecha", TaskDraft(title = "T", dueAt = DUE))
        assertEquals(listOf("con-fecha", "sin-fecha"), emissions.last().sorted())

        repository.moveTask("con-fecha", "columna-hecha", 0)
        assertEquals(listOf("sin-fecha"), emissions.last())

        repository.deleteTask("sin-fecha")
        assertTrue(emissions.last().isEmpty())
    }

    @Test
    fun activeTaskCount_cuentaConYSinFechaSinBorradasNiCompletadas() = runTest {
        val repository = repositoryWithCandidateTasks()

        assertEquals(2, repository.activeTaskCount())
    }

    private fun repositoryWithCandidateTasks(): TaskRepositoryImpl {
        val columnDao = FakeColumnDao().apply {
            put(columnEntity("columna-1"))
            put(columnEntity("columna-hecha", isDone = true))
        }
        val taskDao = FakeTaskDao(columnDao).apply {
            put(taskEntity("con-fecha", columnId = "columna-1", position = 0, dueAt = DUE))
            put(taskEntity("sin-fecha", columnId = "columna-1", position = 1))
            put(taskEntity("borrada", columnId = "columna-1", position = 2, dueAt = DUE, deletedAt = now))
            put(taskEntity("completada", columnId = "columna-hecha", position = 0, dueAt = DUE, completedAt = now))
        }
        return TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { later })
    }

    @Test
    fun deleteTask_deUnaTareaBorrada_fallaConNotFound() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0, updatedAt = now))
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { later })
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
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { now })

        val tasks = repository.observeTasks(BOARD_ID).first()

        assertEquals(listOf("b", "t1", "t2"), tasks.map { it.id })
        assertEquals(listOf(0, 0, 1), tasks.map { it.position })
        assertFalse(tasks.any { it.isDeleted })
    }

    @Test
    fun viewer_noCreaTareaNiLaEscribe() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        val repository = TaskRepositoryImpl(
            taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId,
            now = { now }, gate = gate(com.simposio.taskeer.domain.workspace.WorkspaceRole.VIEWER),
        )

        val result = repository.createTask("columna-1", TaskDraft(title = "Nueva"))

        assertEquals("BOARD_ROLE_FORBIDS", ((result as DomainResult.Failure).error as DomainError.Forbidden).code)
        assertTrue(taskDao.stored.isEmpty())
    }

    @Test
    fun viewer_noBorraLasCompletadasDeUnTableroCompartido() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("c1")) }
        val taskDao = FakeTaskDao(columnDao).apply {
            put(taskEntity("t1", columnId = "c1", position = 0, completedAt = now))
        }
        val repository = TaskRepositoryImpl(
            taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId,
            now = { later }, gate = gate(com.simposio.taskeer.domain.workspace.WorkspaceRole.VIEWER),
        )

        val result = repository.deleteCompleted()

        assertEquals("BOARD_ROLE_FORBIDS", ((result as DomainResult.Failure).error as DomainError.Forbidden).code)
        assertNull(taskDao.stored.getValue("t1").deletedAt)
    }

    @Test
    fun viewer_noMueveNiBorraTareas() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("c1")); put(columnEntity("c2")) }
        val taskDao = FakeTaskDao(columnDao).apply { put(taskEntity("t1", columnId = "c1", position = 0)) }
        val repository = TaskRepositoryImpl(
            taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId,
            now = { later }, gate = gate(com.simposio.taskeer.domain.workspace.WorkspaceRole.VIEWER),
        )

        val moved = repository.moveTask("t1", "c2", 0)
        val deleted = repository.deleteTask("t1")

        assertTrue(moved is DomainResult.Failure && moved.error is DomainError.Forbidden)
        assertTrue(deleted is DomainResult.Failure && deleted.error is DomainError.Forbidden)
        assertEquals("c1", taskDao.stored.getValue("t1").columnId)
        assertNull(taskDao.stored.getValue("t1").deletedAt)
    }

    private fun gate(role: com.simposio.taskeer.domain.workspace.WorkspaceRole?) = BoardPermissionGate(
        object : BoardRoles {
            override suspend fun roleOf(boardId: String) = role

            override fun observeRole(boardId: String) = kotlinx.coroutines.flow.flowOf(role)
        },
    )

    @Test
    fun moveTask_entreColumnas_persisteColumnIdYPosicionesContiguas() = runTest {
        val (taskDao, repository) = moveFixture()

        val result = repository.moveTask("a", "columna-2", 1)

        val moved = (result as DomainResult.Success).value
        assertEquals("columna-2", moved.columnId)
        assertEquals(1, moved.position)
        assertEquals(later, moved.sync.updatedAt)
        assertTrue(moved.sync.pending)
        assertEquals(listOf("b" to 0, "c" to 1), taskDao.column("columna-1"))
        assertEquals(listOf("x" to 0, "a" to 1, "y" to 2), taskDao.column("columna-2"))
        assertEquals(1, taskDao.writeCalls)
    }

    @Test
    fun moveTask_entreColumnas_soloEscribeLasTareasCambiadas() = runTest {
        val (taskDao, repository) = moveFixture()

        repository.moveTask("c", "columna-2", 2)

        assertEquals(now.toEpochMilli(), taskDao.stored.getValue("a").updatedAt)
        assertEquals(now.toEpochMilli(), taskDao.stored.getValue("x").updatedAt)
        assertEquals(later.toEpochMilli(), taskDao.stored.getValue("c").updatedAt)
        assertEquals(now.toEpochMilli(), taskDao.stored.getValue("b").updatedAt)
    }

    @Test
    fun moveTask_aColumnaVacia_usaElIndiceCero() = runTest {
        val (taskDao, repository) = moveFixture()

        val result = repository.moveTask("a", "columna-vacia", 0)

        assertEquals("columna-vacia", (result as DomainResult.Success).value.columnId)
        assertEquals(listOf("a" to 0), taskDao.column("columna-vacia"))
        assertEquals(listOf("b" to 0, "c" to 1), taskDao.column("columna-1"))
    }

    @Test
    fun moveTask_dentroDeLaColumna_reordenaYPersisteContiguo() = runTest {
        val (taskDao, repository) = moveFixture()

        val result = repository.moveTask("c", "columna-1", 0)

        assertEquals(0, (result as DomainResult.Success).value.position)
        assertEquals(listOf("c" to 0, "a" to 1, "b" to 2), taskDao.column("columna-1"))
    }

    @Test
    fun moveTask_alMismoIndice_noEscribeYDevuelveLaTareaIgual() = runTest {
        val (taskDao, repository) = moveFixture()
        val writesBefore = taskDao.writeCalls

        val result = repository.moveTask("b", "columna-1", 1)

        assertEquals(taskDao.stored.getValue("b").toTask(), (result as DomainResult.Success).value)
        assertEquals(writesBefore, taskDao.writeCalls)
    }

    @Test
    fun moveTask_tareaInexistenteOBorrada_fallaConTaskNotFoundSinEscribir() = runTest {
        val (taskDao, repository) = moveFixture()
        val writesBefore = taskDao.writeCalls

        val missing = repository.moveTask("nada", "columna-2", 0)
        val deleted = repository.moveTask("borrada", "columna-2", 0)

        listOf(missing, deleted).forEach {
            val error = (it as DomainResult.Failure).error
            assertTrue(error is DomainError.NotFound)
            assertEquals("TASK_NOT_FOUND", error.code)
        }
        assertEquals(writesBefore, taskDao.writeCalls)
    }

    @Test
    fun moveTask_columnaDestinoInexistenteOBorrada_fallaConColumnNotFound() = runTest {
        val (taskDao, repository) = moveFixture()
        val writesBefore = taskDao.writeCalls

        val missing = repository.moveTask("a", "columna-x", 0)
        val deleted = repository.moveTask("a", "columna-borrada", 0)

        listOf(missing, deleted).forEach {
            val error = (it as DomainResult.Failure).error
            assertTrue(error is DomainError.NotFound)
            assertEquals("COLUMN_NOT_FOUND", error.code)
        }
        assertEquals(writesBefore, taskDao.writeCalls)
    }

    @Test
    fun moveTask_aColumnaDeOtroTablero_fallaConColumnBoardMismatchSinEscribir() = runTest {
        val (taskDao, repository) = moveFixture()
        val writesBefore = taskDao.writeCalls

        val result = repository.moveTask("a", "columna-ajena", 0)

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.Validation)
        assertEquals("COLUMN_BOARD_MISMATCH", error.code)
        assertEquals("columna-1", taskDao.stored.getValue("a").columnId)
        assertEquals(writesBefore, taskDao.writeCalls)
    }

    @Test
    fun moveTask_indiceFueraDeRango_fallaConIndexOutOfBoundsSinEscribir() = runTest {
        val (taskDao, repository) = moveFixture()
        val writesBefore = taskDao.writeCalls

        val beyondDestination = repository.moveTask("a", "columna-2", 3)
        val beyondSame = repository.moveTask("a", "columna-1", 3)
        val negative = repository.moveTask("a", "columna-2", -1)

        listOf(beyondDestination, beyondSame, negative).forEach {
            val error = (it as DomainResult.Failure).error
            assertTrue(error is DomainError.Validation)
            assertEquals("TASK_INDEX_OUT_OF_BOUNDS", error.code)
        }
        assertEquals(writesBefore, taskDao.writeCalls)
    }

    @Test
    fun moveTask_ordenDeComprobacion_tareaLuegoColumnaLuegoTableroLuegoIndice() = runTest {
        val (_, repository) = moveFixture()

        assertEquals("TASK_NOT_FOUND", (repository.moveTask("nada", "columna-x", 99) as DomainResult.Failure).error.code)
        assertEquals("COLUMN_NOT_FOUND", (repository.moveTask("a", "columna-x", 99) as DomainResult.Failure).error.code)
        assertEquals("COLUMN_BOARD_MISMATCH", (repository.moveTask("a", "columna-ajena", 99) as DomainResult.Failure).error.code)
        assertEquals("TASK_INDEX_OUT_OF_BOUNDS", (repository.moveTask("a", "columna-2", 99) as DomainResult.Failure).error.code)
    }

    @Test
    fun moveTask_conHuecosEnLasPosiciones_losNormalizaAlMover() = runTest {
        val columnDao = FakeColumnDao().apply {
            put(columnEntity("columna-1"))
            put(columnEntity("columna-2"))
        }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("a", columnId = "columna-1", position = 0))
        taskDao.put(taskEntity("b", columnId = "columna-1", position = 5))
        taskDao.put(taskEntity("c", columnId = "columna-1", position = 5))
        taskDao.put(taskEntity("x", columnId = "columna-2", position = 3))
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { later })

        repository.moveTask("a", "columna-2", 0)

        assertEquals(listOf("b" to 0, "c" to 1), taskDao.column("columna-1"))
        assertEquals(listOf("a" to 0, "x" to 1), taskDao.column("columna-2"))
    }

    @Test
    fun moveTask_loAlmacenadoEsLoQueObservaObserveTasksTrasReiniciar() = runTest {
        val (taskDao, repository) = moveFixture()
        repository.moveTask("a", "columna-2", 0)

        val reopened = TaskRepositoryImpl(taskDao, taskDao.columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { later })
        val tasks = reopened.observeTasks(BOARD_ID).first()

        assertEquals(
            listOf("a" to "columna-2", "b" to "columna-1", "c" to "columna-1", "x" to "columna-2", "y" to "columna-2"),
            tasks.map { it.id to it.columnId },
        )
        assertEquals(0, tasks.first { it.id == "a" }.position)
        assertEquals("columna-2", (reopened.getTask("a") as DomainResult.Success).value.columnId)
    }

    @Test
    fun createTask_columnaLlena_fallaConWipLimitReachedSinEscribir() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1", wipLimit = 2)) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t0", columnId = "columna-1", position = 0))
        taskDao.put(taskEntity("t1", columnId = "columna-1", position = 1))
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { now })
        val writesBefore = taskDao.writeCalls

        val error = (repository.createTask("columna-1", TaskDraft(title = "Nueva")) as DomainResult.Failure).error

        assertTrue(error is DomainError.Conflict)
        assertEquals("COLUMN_WIP_LIMIT_REACHED", error.code)
        assertEquals(writesBefore, taskDao.writeCalls)
    }

    @Test
    fun createTask_columnaConHueco_crea() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1", wipLimit = 2)) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t0", columnId = "columna-1", position = 0))
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { now })

        val result = repository.createTask("columna-1", TaskDraft(title = "Nueva"))

        assertEquals(1, (result as DomainResult.Success).value.position)
    }

    @Test
    fun createTask_sinLimite_creaAunqueHayaMuchas() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        repeat(5) { taskDao.put(taskEntity("t$it", columnId = "columna-1", position = it)) }
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { now })

        assertTrue(repository.createTask("columna-1", TaskDraft(title = "Nueva")) is DomainResult.Success)
    }

    @Test
    fun createTask_tareaBorradaLiberaCupo() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1", wipLimit = 1)) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t0", columnId = "columna-1", position = 0, deletedAt = now))
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { now })

        assertTrue(repository.createTask("columna-1", TaskDraft(title = "Nueva")) is DomainResult.Success)
    }

    @Test
    fun createTask_tituloVacioEnColumnaLlena_devuelveTituloRequerido() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1", wipLimit = 1)) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t0", columnId = "columna-1", position = 0))
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { now })

        val result = repository.createTask("columna-1", TaskDraft(title = "  "))

        assertEquals("TASK_TITLE_REQUIRED", (result as DomainResult.Failure).error.code)
    }

    @Test
    fun createTask_columnaInexistenteConLimite_devuelveColumnNotFound() = runTest {
        val columnDao = FakeColumnDao()
        val taskDao = FakeTaskDao(columnDao)
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { now })

        val result = repository.createTask("nada", TaskDraft(title = "Nueva"))

        assertEquals("COLUMN_NOT_FOUND", (result as DomainResult.Failure).error.code)
    }

    @Test
    fun moveTask_aColumnaLlena_fallaConWipLimitReachedSinEscribir() = runTest {
        val (taskDao, repository) = moveFixture(limitColumn2 = 2)
        val writesBefore = taskDao.writeCalls

        val error = (repository.moveTask("a", "columna-2", 0) as DomainResult.Failure).error

        assertTrue(error is DomainError.Conflict)
        assertEquals("COLUMN_WIP_LIMIT_REACHED", error.code)
        assertEquals(writesBefore, taskDao.writeCalls)
        assertEquals("columna-1", taskDao.stored.getValue("a").columnId)
    }

    @Test
    fun moveTask_aColumnaConHueco_mueve() = runTest {
        val (_, repository) = moveFixture(limitColumn2 = 3)

        val result = repository.moveTask("a", "columna-2", 0)

        assertEquals("columna-2", (result as DomainResult.Success).value.columnId)
    }

    @Test
    fun moveTask_dentroDeColumnaLlena_reordena() = runTest {
        val (taskDao, repository) = moveFixture(limitColumn1 = 3)

        val result = repository.moveTask("c", "columna-1", 0)

        assertTrue(result is DomainResult.Success)
        assertEquals(listOf("c" to 0, "a" to 1, "b" to 2), taskDao.column("columna-1"))
    }

    @Test
    fun moveTask_salirDeColumnaSobrepasada_sePermite() = runTest {
        val (_, repository) = moveFixture(limitColumn1 = 1, limitColumnVacia = 5)

        val result = repository.moveTask("a", "columna-vacia", 0)

        assertEquals("columna-vacia", (result as DomainResult.Success).value.columnId)
    }

    @Test
    fun moveTask_indiceInvalidoConDestinoLleno_devuelveIndexOutOfBounds() = runTest {
        val (_, repository) = moveFixture(limitColumn2 = 2)

        val result = repository.moveTask("a", "columna-2", 99)

        assertEquals("TASK_INDEX_OUT_OF_BOUNDS", (result as DomainResult.Failure).error.code)
    }

    @Test
    fun moveTask_otroTableroConLimite_devuelveColumnBoardMismatch() = runTest {
        val (_, repository) = moveFixture(limitAjena = 1)

        val result = repository.moveTask("a", "columna-ajena", 0)

        assertEquals("COLUMN_BOARD_MISMATCH", (result as DomainResult.Failure).error.code)
    }

    @Test
    fun moveTask_destinoSinLimite_noBloquea() = runTest {
        val (_, repository) = moveFixture()

        assertTrue(repository.moveTask("a", "columna-2", 0) is DomainResult.Success)
    }

    private fun moveFixture(
        limitColumn1: Int? = null,
        limitColumn2: Int? = null,
        limitColumnVacia: Int? = null,
        limitAjena: Int? = null,
    ): Pair<FakeTaskDao, TaskRepositoryImpl> {
        val columnDao = FakeColumnDao().apply {
            put(columnEntity("columna-1", wipLimit = limitColumn1))
            put(columnEntity("columna-2", wipLimit = limitColumn2))
            put(columnEntity("columna-vacia", wipLimit = limitColumnVacia))
            put(columnEntity("columna-borrada", deleted = true))
            put(columnEntity("columna-ajena", boardId = "tablero-2", wipLimit = limitAjena))
        }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("a", columnId = "columna-1", position = 0))
        taskDao.put(taskEntity("b", columnId = "columna-1", position = 1))
        taskDao.put(taskEntity("c", columnId = "columna-1", position = 2))
        taskDao.put(taskEntity("x", columnId = "columna-2", position = 0))
        taskDao.put(taskEntity("y", columnId = "columna-2", position = 1))
        taskDao.put(taskEntity("borrada", columnId = "columna-1", position = 3, deletedAt = now))
        return taskDao to TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { later })
    }

    private fun completionFixture(
        todoWip: Int? = null,
        doneWip: Int? = null,
        withDone: Boolean = true,
        withOpen: Boolean = true,
        zone: ZoneId = ZoneOffset.UTC,
    ): Pair<FakeTaskDao, TaskRepositoryImpl> {
        val columnDao = FakeColumnDao().apply {
            if (withOpen) {
                put(columnEntity("todo", position = 0, wipLimit = todoWip))
                put(columnEntity("doing", position = 1))
            }
            if (withDone) {
                put(columnEntity("done2", position = 3, isDone = true))
                put(columnEntity("done", position = 2, isDone = true, wipLimit = doneWip))
            }
        }
        val taskDao = FakeTaskDao(columnDao)
        return taskDao to TaskRepositoryImpl(
            taskDao,
            columnDao,
            taskDao.subtaskDao,
            taskDao.reminderDao,
            taskDao.listDao,
            taskDao.defaultListId,
            now = { later },
            timeZone = { zone },
        )
    }

    @Test
    fun setCompleted_enBandeja_fijaCompletedAtSinMover() = runTest {
        val (taskDao, repository) = completionFixture()
        taskDao.put(taskEntity("i", columnId = null, position = 4))

        val done = (repository.setCompleted("i", true) as DomainResult.Success).value

        assertEquals(later, done.completedAt)
        assertNull(done.columnId)
        assertEquals(4, done.position)
        assertEquals(later.toEpochMilli(), taskDao.stored.getValue("i").completedAt)
        assertEquals(1, taskDao.writeCalls)
    }

    @Test
    fun setCompleted_false_enBandeja_limpiaCompletedAt() = runTest {
        val (taskDao, repository) = completionFixture()
        taskDao.put(taskEntity("i", columnId = null, position = 4, completedAt = now))

        repository.setCompleted("i", false)

        val row = taskDao.stored.getValue("i")
        assertNull(row.completedAt)
        assertNull(row.columnId)
        assertEquals(4, row.position)
        assertTrue(row.pending)
    }

    @Test
    fun setCompleted_conColumnaDeCierre_laMueveAlFinalYRenumeraOrigen() = runTest {
        val (taskDao, repository) = completionFixture()
        taskDao.put(taskEntity("a", columnId = "todo", position = 0))
        taskDao.put(taskEntity("b", columnId = "todo", position = 1))
        taskDao.put(taskEntity("c", columnId = "todo", position = 2))
        taskDao.put(taskEntity("x", columnId = "done", position = 0, completedAt = now))

        val done = (repository.setCompleted("b", true) as DomainResult.Success).value

        assertEquals("done", done.columnId)
        assertEquals(1, done.position)
        assertEquals(later, done.completedAt)
        assertEquals(listOf("a" to 0, "c" to 1), taskDao.column("todo"))
        assertEquals(listOf("x" to 0, "b" to 1), taskDao.column("done"))
        assertEquals(1, taskDao.writeCalls)
    }

    @Test
    fun setCompleted_yaEnColumnaDeCierre_noCambiaDeColumna() = runTest {
        val (taskDao, repository) = completionFixture()
        taskDao.put(taskEntity("a", columnId = "done2", position = 0))

        val done = (repository.setCompleted("a", true) as DomainResult.Success).value

        assertEquals("done2", done.columnId)
        assertEquals(0, done.position)
        assertEquals(later, done.completedAt)
    }

    @Test
    fun setCompleted_sinColumnaDeCierre_laDejaEnSuColumna() = runTest {
        val (taskDao, repository) = completionFixture(withDone = false)
        taskDao.put(taskEntity("a", columnId = "todo", position = 0))
        taskDao.put(taskEntity("b", columnId = "todo", position = 1))

        val done = (repository.setCompleted("a", true) as DomainResult.Success).value

        assertEquals("todo", done.columnId)
        assertEquals(0, done.position)
        assertEquals(later, done.completedAt)
        assertEquals(listOf("a" to 0, "b" to 1), taskDao.column("todo"))
    }

    @Test
    fun setCompleted_false_desdeColumnaDeCierre_laMueveALaPrimeraColumnaAbierta() = runTest {
        val (taskDao, repository) = completionFixture()
        taskDao.put(taskEntity("t0", columnId = "todo", position = 0))
        taskDao.put(taskEntity("h", columnId = "done", position = 0, completedAt = now))
        taskDao.put(taskEntity("k", columnId = "done", position = 1, completedAt = now))

        val reopened = (repository.setCompleted("h", false) as DomainResult.Success).value

        assertEquals("todo", reopened.columnId)
        assertEquals(1, reopened.position)
        assertNull(reopened.completedAt)
        assertEquals(listOf("t0" to 0, "h" to 1), taskDao.column("todo"))
        assertEquals(listOf("k" to 0), taskDao.column("done"))
        assertEquals(1, taskDao.writeCalls)
    }

    @Test
    fun setCompleted_false_fueraDeColumnaDeCierre_soloLimpiaCompletedAt() = runTest {
        val (taskDao, repository) = completionFixture()
        taskDao.put(taskEntity("a", columnId = "doing", position = 0, completedAt = now))

        val reopened = (repository.setCompleted("a", false) as DomainResult.Success).value

        assertEquals("doing", reopened.columnId)
        assertEquals(0, reopened.position)
        assertNull(reopened.completedAt)
    }

    @Test
    fun setCompleted_false_sinColumnaAbierta_devuelveTaskNoOpenColumn() = runTest {
        val (taskDao, repository) = completionFixture(withOpen = false)
        taskDao.put(taskEntity("h", columnId = "done", position = 0, completedAt = now))

        val error = (repository.setCompleted("h", false) as DomainResult.Failure).error

        assertEquals("TASK_NO_OPEN_COLUMN", error.code)
        assertEquals(0, taskDao.writeCalls)
        assertEquals(now.toEpochMilli(), taskDao.stored.getValue("h").completedAt)
    }

    @Test
    fun setCompleted_destinoLleno_devuelveWipYNoEscribe() = runTest {
        val (taskDao, repository) = completionFixture(doneWip = 1)
        taskDao.put(taskEntity("a", columnId = "todo", position = 0))
        taskDao.put(taskEntity("x", columnId = "done", position = 0, completedAt = now))

        val error = (repository.setCompleted("a", true) as DomainResult.Failure).error

        assertEquals("COLUMN_WIP_LIMIT_REACHED", error.code)
        assertEquals(0, taskDao.writeCalls)
        assertNull(taskDao.stored.getValue("a").completedAt)
    }

    @Test
    fun setCompleted_false_destinoLleno_devuelveWipYNoEscribe() = runTest {
        val (taskDao, repository) = completionFixture(todoWip = 1)
        taskDao.put(taskEntity("t0", columnId = "todo", position = 0))
        taskDao.put(taskEntity("h", columnId = "done", position = 0, completedAt = now))

        val error = (repository.setCompleted("h", false) as DomainResult.Failure).error

        assertEquals("COLUMN_WIP_LIMIT_REACHED", error.code)
        assertEquals(0, taskDao.writeCalls)
        assertEquals("done", taskDao.stored.getValue("h").columnId)
    }

    @Test
    fun setCompleted_tareaInexistente_devuelveTaskNotFound() = runTest {
        val (taskDao, repository) = completionFixture()
        taskDao.put(taskEntity("borrada", columnId = "todo", position = 0, deletedAt = now))

        assertEquals("TASK_NOT_FOUND", (repository.setCompleted("nada", true) as DomainResult.Failure).error.code)
        assertEquals("TASK_NOT_FOUND", (repository.setCompleted("borrada", true) as DomainResult.Failure).error.code)
        assertEquals(0, taskDao.writeCalls)
    }

    @Test
    fun setCompleted_mismoEstado_noEscribe() = runTest {
        val (taskDao, repository) = completionFixture()
        taskDao.put(taskEntity("a", columnId = "todo", position = 0))

        val same = (repository.setCompleted("a", false) as DomainResult.Success).value

        assertEquals("a", same.id)
        assertEquals(0, taskDao.writeCalls)
    }

    @Test
    fun moveTask_aColumnaDeCierre_fijaCompletedAt() = runTest {
        val (taskDao, repository) = completionFixture()
        taskDao.put(taskEntity("a", columnId = "todo", position = 0))

        val moved = (repository.moveTask("a", "done", 0) as DomainResult.Success).value

        assertEquals(later, moved.completedAt)
        assertEquals(later.toEpochMilli(), taskDao.stored.getValue("a").completedAt)
    }

    @Test
    fun moveTask_desdeColumnaDeCierre_limpiaCompletedAt() = runTest {
        val (taskDao, repository) = completionFixture()
        taskDao.put(taskEntity("h", columnId = "done", position = 0, completedAt = now))

        repository.moveTask("h", "todo", 0)

        assertNull(taskDao.stored.getValue("h").completedAt)
    }

    @Test
    fun moveTask_entreColumnasAbiertas_conservaCompletedAt() = runTest {
        val (taskDao, repository) = completionFixture()
        taskDao.put(taskEntity("a", columnId = "todo", position = 0, completedAt = now))

        repository.moveTask("a", "doing", 0)

        assertEquals(now.toEpochMilli(), taskDao.stored.getValue("a").completedAt)
    }

    @Test
    fun assignToColumn_aColumnaDeCierre_fijaCompletedAt() = runTest {
        val (taskDao, repository) = completionFixture()
        taskDao.put(taskEntity("i", columnId = null, position = 0))
        taskDao.put(taskEntity("j", columnId = null, position = 1, completedAt = now))

        val toDone = (repository.assignToColumn("i", "done") as DomainResult.Success).value
        val toOpen = (repository.assignToColumn("j", "todo") as DomainResult.Success).value

        assertEquals(later, toDone.completedAt)
        assertEquals(now, toOpen.completedAt)
    }

    private fun repeating(id: String, columnId: String?, position: Int, dueAt: Instant? = REPEAT_DUE): TaskEntity =
        taskEntity(id, columnId, position, dueAt = dueAt).copy(
            repeatUnit = "DAY",
            repeatInterval = 1,
            repeatWeekdays = 0,
            repeatAnchorDay = 21,
        )

    private fun FakeTaskDao.successorOf(id: String): TaskEntity = stored.getValue(successorIdFor(id))

    @Test
    fun setCompleted_tareaRepetida_creaLaSiguienteConNuevaFecha() = runTest {
        val (taskDao, repository) = completionFixture(withDone = false)
        taskDao.put(repeating("a", "todo", 0))

        repository.setCompleted("a", true)

        val next = taskDao.successorOf("a")
        assertEquals(Instant.parse("2026-10-22T09:00:00Z").toEpochMilli(), next.dueAt)
        assertNull(next.completedAt)
        assertNull(next.repeatSuccessorId)
        assertEquals("Tarea a", next.title)
        assertEquals(successorIdFor("a"), taskDao.stored.getValue("a").repeatSuccessorId)
        assertEquals(later.toEpochMilli(), taskDao.stored.getValue("a").completedAt)
        assertEquals(1, taskDao.successorCalls)
    }

    @Test
    fun setCompleted_tareaRepetida_enColumnaDeCierre_laSiguienteQuedaEnLaColumnaDeOrigen() = runTest {
        val (taskDao, repository) = completionFixture()
        taskDao.put(taskEntity("a", columnId = "todo", position = 0))
        taskDao.put(repeating("b", "todo", 1))
        taskDao.put(taskEntity("c", columnId = "todo", position = 2))

        repository.setCompleted("b", true)

        assertEquals("done", taskDao.stored.getValue("b").columnId)
        assertEquals(listOf("a" to 0, "c" to 1, successorIdFor("b") to 2), taskDao.column("todo"))
        assertEquals(1, taskDao.successorCalls)
    }

    @Test
    fun setCompleted_tareaRepetidaEnBandeja_laSiguienteQuedaEnBandeja() = runTest {
        val (taskDao, repository) = completionFixture()
        taskDao.put(repeating("i", null, 0))

        repository.setCompleted("i", true)

        assertNull(taskDao.successorOf("i").columnId)
        assertNull(taskDao.stored.getValue("i").columnId)
    }

    @Test
    fun setCompleted_tareaRepetida_sinColumnaDeCierre_laSiguienteVaAlFinalDeLaColumna() = runTest {
        val (taskDao, repository) = completionFixture(withDone = false)
        taskDao.put(repeating("a", "todo", 0))
        taskDao.put(taskEntity("b", columnId = "todo", position = 1))

        repository.setCompleted("a", true)

        assertEquals(listOf("a" to 0, "b" to 1, successorIdFor("a") to 2), taskDao.column("todo"))
    }

    @Test
    fun setCompleted_tareaRepetida_columnaConWipLleno_creaLaSiguienteSinFallar() = runTest {
        val (taskDao, repository) = completionFixture(todoWip = 2, withDone = false)
        taskDao.put(repeating("a", "todo", 0))
        taskDao.put(taskEntity("b", columnId = "todo", position = 1))

        val result = repository.setCompleted("a", true)

        assertTrue(result is DomainResult.Success)
        assertEquals("todo", taskDao.successorOf("a").columnId)
        assertEquals(3, taskDao.column("todo").size)
    }

    @Test
    fun setCompleted_tareaRepetida_noAnadeLaSiguienteAMiDia() = runTest {
        val (taskDao, repository) = completionFixture()
        taskDao.put(repeating("a", "todo", 0))

        repository.setCompleted("a", true)

        assertTrue(taskDao.myDayEntries.isEmpty())
    }

    @Test
    fun setCompleted_dosVeces_noDuplicaLaSiguiente() = runTest {
        val (taskDao, repository) = completionFixture(withDone = false)
        taskDao.put(repeating("a", "todo", 0))

        repository.setCompleted("a", true)
        repository.setCompleted("a", true)

        assertEquals(1, taskDao.successorCalls)
        assertEquals(setOf("a", successorIdFor("a")), taskDao.stored.keys)
    }

    @Test
    fun setCompleted_completarDesmarcarYCompletar_noCreaOtraSucesora() = runTest {
        val (taskDao, repository) = completionFixture(withDone = false)
        taskDao.put(repeating("a", "todo", 0))

        repository.setCompleted("a", true)
        repository.setCompleted("a", false)
        repository.setCompleted("a", true)

        assertEquals(1, taskDao.successorCalls)
        assertEquals(2, taskDao.stored.size)
    }

    @Test
    fun setCompleted_false_tareaRepetida_conservaLaSucesora() = runTest {
        val (taskDao, repository) = completionFixture(withDone = false)
        taskDao.put(repeating("a", "todo", 0))
        repository.setCompleted("a", true)
        val successorBefore = taskDao.successorOf("a")

        repository.setCompleted("a", false)

        assertEquals(successorBefore, taskDao.successorOf("a"))
        assertEquals(successorIdFor("a"), taskDao.stored.getValue("a").repeatSuccessorId)
        assertNull(taskDao.stored.getValue("a").completedAt)
    }

    @Test
    fun setCompleted_tareaRepetidaSinFecha_completaSinCrearSiguiente() = runTest {
        val (taskDao, repository) = completionFixture(withDone = false)
        taskDao.put(repeating("a", "todo", 0, dueAt = null))

        val result = repository.setCompleted("a", true)

        assertEquals(later, (result as DomainResult.Success).value.completedAt)
        assertEquals(0, taskDao.successorCalls)
        assertEquals(setOf("a"), taskDao.stored.keys)
    }

    @Test
    fun setCompleted_tareaRepetida_copiaSubtareasActivasSinCompletar() = runTest {
        val (taskDao, repository) = completionFixture(withDone = false)
        taskDao.put(repeating("a", "todo", 0))
        taskDao.putSubtask(subtaskEntity("s1", "a").copy(completed = true, position = 0))
        taskDao.putSubtask(subtaskEntity("s2", "a", deletedAt = now.toEpochMilli()))

        repository.setCompleted("a", true)

        val copies = taskDao.subtasks.values.filter { it.taskId == successorIdFor("a") }
        assertEquals(1, copies.size)
        assertEquals("Subtarea s1", copies.single().title)
        assertFalse(copies.single().completed)
        assertFalse(copies.single().id == "s1")
        assertTrue(taskDao.subtasks.getValue("s1").completed)
    }

    @Test
    fun setCompleted_tareaRepetida_copiaElRecordatorioConElMismoDesfase() = runTest {
        val (taskDao, repository) = completionFixture(withDone = false)
        taskDao.put(repeating("a", "todo", 0))
        taskDao.putReminder(reminderEntity("r1", "a", "SENT"))

        repository.setCompleted("a", true)

        val copy = taskDao.reminders.values.single { it.taskId == successorIdFor("a") }
        assertEquals("PENDING", copy.status)
        assertEquals(Instant.parse("2026-10-22T10:01:00Z").toEpochMilli(), copy.remindAt)
        assertEquals("SENT", taskDao.reminders.getValue("r1").status)
    }

    @Test
    fun setCompleted_tareaRepetida_sinRecordatorio_noCreaRecordatorio() = runTest {
        val (taskDao, repository) = completionFixture(withDone = false)
        taskDao.put(repeating("a", "todo", 0))

        repository.setCompleted("a", true)

        assertTrue(taskDao.reminders.isEmpty())
    }

    @Test
    fun setCompleted_tareaRepetida_usaLaZonaInyectada() = runTest {
        val dueAt = Instant.parse("2026-10-20T03:00:00Z")
        val (utcDao, utcRepository) = completionFixture(withDone = false)
        val (mexicoDao, mexicoRepository) = completionFixture(withDone = false, zone = ZoneId.of("America/Mexico_City"))
        utcDao.put(repeating("a", "todo", 0, dueAt))
        mexicoDao.put(repeating("a", "todo", 0, dueAt))

        utcRepository.setCompleted("a", true)
        mexicoRepository.setCompleted("a", true)

        assertEquals(Instant.parse("2026-10-21T03:00:00Z").toEpochMilli(), utcDao.successorOf("a").dueAt)
        assertEquals(Instant.parse("2026-10-22T03:00:00Z").toEpochMilli(), mexicoDao.successorOf("a").dueAt)
    }

    @Test
    fun moveTask_aColumnaDeCierre_tareaRepetida_creaLaSiguiente() = runTest {
        val (taskDao, repository) = completionFixture()
        taskDao.put(repeating("a", "todo", 0))
        taskDao.put(taskEntity("b", columnId = "todo", position = 1))

        repository.moveTask("a", "done", 0)

        assertEquals(listOf("b" to 0, successorIdFor("a") to 1), taskDao.column("todo"))
        assertEquals(successorIdFor("a"), taskDao.stored.getValue("a").repeatSuccessorId)
        assertEquals(1, taskDao.successorCalls)
    }

    @Test
    fun assignToColumn_aColumnaDeCierre_tareaRepetida_creaLaSiguiente() = runTest {
        val (taskDao, repository) = completionFixture()
        taskDao.put(repeating("i", null, 0))

        repository.assignToColumn("i", "done")

        assertEquals("done", taskDao.stored.getValue("i").columnId)
        assertNull(taskDao.successorOf("i").columnId)
        assertEquals(successorIdFor("i"), taskDao.stored.getValue("i").repeatSuccessorId)
        assertEquals(1, taskDao.successorCalls)
    }

    @Test
    fun deleteTask_deLaOriginal_noTocaLaSucesora() = runTest {
        val (taskDao, repository) = completionFixture(withDone = false)
        taskDao.put(repeating("a", "todo", 0))
        repository.setCompleted("a", true)
        val successorBefore = taskDao.successorOf("a")

        repository.deleteTask("a")

        assertEquals(successorBefore, taskDao.successorOf("a"))
        assertNull(taskDao.successorOf("a").deletedAt)
    }

    private fun FakeTaskDao.column(columnId: String): List<Pair<String, Int>> =
        stored.values
            .filter { it.columnId == columnId && it.deletedAt == null }
            .sortedBy { it.position }
            .map { it.id to it.position }

    private fun columnEntity(
        id: String,
        deleted: Boolean = false,
        boardId: String = BOARD_ID,
        wipLimit: Int? = null,
        isDone: Boolean = false,
        position: Int = 0,
    ): ColumnEntity = ColumnEntity(
        id = id,
        boardId = boardId,
        name = "Por hacer",
        position = position,
        wipLimit = wipLimit,
        isDoneColumn = isDone,
        createdAt = 0L,
        updatedAt = now.toEpochMilli(),
        pending = true,
        deletedAt = if (deleted) now.toEpochMilli() else null,
    )

    @Test
    fun createTask_tituloDe201_noEscribeYNombraElLimite() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { now })

        val result = repository.createTask("columna-1", TaskDraft(title = "x".repeat(201)))

        val error = (result as DomainResult.Failure).error
        assertEquals("TASK_TITLE_TOO_LONG", error.code)
        assertTrue(error.message.contains("200"))
        assertEquals(0, taskDao.writeCalls)
    }

    @Test
    fun createTask_descripcionDe5001_noEscribe() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { now })

        val result = repository.createTask("columna-1", TaskDraft(title = "T", description = "d".repeat(5001)))

        val error = (result as DomainResult.Failure).error
        assertEquals("TASK_DESCRIPTION_TOO_LONG", error.code)
        assertTrue(error.message.contains("5000"))
        assertEquals(0, taskDao.writeCalls)
    }

    @Test
    fun updateTask_tituloDe201_noEscribe() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        val writesBefore = taskDao.writeCalls
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { later })

        val result = repository.updateTask("t1", TaskDraft(title = "x".repeat(201)))

        assertEquals("TASK_TITLE_TOO_LONG", (result as DomainResult.Failure).error.code)
        assertEquals(writesBefore, taskDao.writeCalls)
    }

    @Test
    fun createTask_fijaCreatedAtYUpdateTaskLoConserva() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        var clock = now
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { clock })

        val created = (repository.createTask("columna-1", TaskDraft(title = "T")) as DomainResult.Success).value
        clock = later
        repository.updateTask(created.id, TaskDraft(title = "U"))

        val row = taskDao.stored.getValue(created.id)
        assertEquals(now.toEpochMilli(), row.createdAt)
        assertEquals(later.toEpochMilli(), row.updatedAt)
    }

    @Test
    fun createTaskWithExtras_conMiDia_escribeTareaYEntradaEnUnaLlamada() = runTest {
        val (repository, taskDao) = extrasRepository()

        val created = (
            repository.createTaskWithExtras(
                "columna-1",
                TaskCreation(TaskDraft(title = "Informe"), addToMyDay = true),
            ) as DomainResult.Success
            ).value

        val entry = taskDao.myDayEntries.getValue(created.id)
        assertEquals(1, taskDao.extrasCalls)
        assertEquals(now.toEpochMilli(), entry.addedAt)
        assertTrue(entry.pending && entry.deletedAt == null)
    }

    @Test
    fun createTaskWithExtras_sinMiDia_noEscribeEntrada() = runTest {
        val (repository, taskDao) = extrasRepository()

        repository.createTaskWithExtras("columna-1", TaskCreation(TaskDraft(title = "Informe")))

        assertTrue(taskDao.myDayEntries.isEmpty())
    }

    @Test
    fun createTaskWithExtras_enColumna_ubicaAlFinal() = runTest {
        val (repository, taskDao) = extrasRepository()
        taskDao.put(taskEntity("t0", columnId = "columna-1", position = 0))
        taskDao.put(taskEntity("t1", columnId = "columna-1", position = 1))

        val created = (
            repository.createTaskWithExtras(
                "columna-1",
                TaskCreation(TaskDraft(title = "Informe"), addToMyDay = true),
            ) as DomainResult.Success
            ).value

        assertEquals(2, created.position)
        assertEquals("columna-1", taskDao.stored.getValue(created.id).columnId)
    }

    @Test
    fun createTaskWithExtras_enBandeja_noConsultaColumnas() = runTest {
        val columnDao = FakeColumnDao()
        val taskDao = FakeTaskDao(columnDao)
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { now })

        val created = (
            repository.createTaskWithExtras(null, TaskCreation(TaskDraft(title = "Pan"), addToMyDay = true))
                as DomainResult.Success
            ).value

        assertNull(taskDao.stored.getValue(created.id).columnId)
        assertEquals(LocalUser.ID, created.assigneeId)
        assertTrue(taskDao.stored.getValue(created.id).pending)
    }

    @Test
    fun createTaskWithExtras_enLista_ubicaAlFinalDeLaLista() = runTest {
        val columnDao = FakeColumnDao()
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("b0", columnId = null, position = 0))
        taskDao.put(taskEntity("b1", columnId = null, position = 1))
        taskDao.put(taskEntity("borrada", columnId = null, position = 2, deletedAt = now))
        val repository = TaskRepositoryImpl(taskDao, columnDao, taskDao.subtaskDao, taskDao.reminderDao, taskDao.listDao, taskDao.defaultListId, now = { now })

        val created = (
            repository.createTaskWithExtras(null, TaskCreation(TaskDraft(title = "Pan")))
                as DomainResult.Success
            ).value

        assertEquals(2, created.position)
        assertEquals(SEEDED_TASK_LIST_ID, created.listId)
        assertEquals(1, taskDao.countInListCalls)
    }

    @Test
    fun createTaskWithExtras_conMiDiaYColumnaLlena_noEscribeNada() = runTest {
        val (repository, taskDao) = extrasRepository(wipLimit = 1)
        taskDao.put(taskEntity("t0", columnId = "columna-1", position = 0))

        val error = failureOf(
            repository.createTaskWithExtras(
                "columna-1",
                TaskCreation(TaskDraft(title = "Informe"), addToMyDay = true),
            ),
        )

        assertEquals("COLUMN_WIP_LIMIT_REACHED", error.code)
        assertTrue(taskDao.myDayEntries.isEmpty())
        assertEquals(setOf("t0"), taskDao.stored.keys)
    }

    @Test
    fun createTaskWithExtras_conMiDiaYTituloInvalido_noEscribeNada() = runTest {
        val (repository, taskDao) = extrasRepository()

        val error = failureOf(
            repository.createTaskWithExtras(
                null,
                TaskCreation(TaskDraft(title = "   "), addToMyDay = true),
            ),
        )

        assertEquals("TASK_TITLE_REQUIRED", error.code)
        assertTrue(taskDao.myDayEntries.isEmpty())
        assertTrue(taskDao.stored.isEmpty())
    }

    @Test
    fun assignToColumn_daColumnaYPosicionFinal() = runTest {
        val (repository, taskDao) = extrasRepository()
        taskDao.put(taskEntity("x", columnId = "columna-1", position = 0))
        taskDao.put(taskEntity("borrada", columnId = "columna-1", position = 1, deletedAt = now))
        taskDao.put(taskEntity("pan", columnId = null, position = 0))

        val result = repository.assignToColumn("pan", "columna-1")

        val assigned = (result as DomainResult.Success).value
        val row = taskDao.stored.getValue("pan")
        assertEquals("columna-1", row.columnId)
        assertEquals(1, row.position)
        assertEquals(assigned.columnId, row.columnId)
        assertTrue(row.pending)
    }

    @Test
    fun assignToColumn_noTocaSubtareasNiRecordatorios() = runTest {
        val (repository, taskDao) = extrasRepository()
        taskDao.put(taskEntity("pan", columnId = null, position = 0))
        taskDao.putSubtask(subtaskEntity("s1", "pan"))
        taskDao.putReminder(reminderEntity("r1", "pan", "PENDING"))
        val subtaskBefore = taskDao.subtasks.getValue("s1")
        val reminderBefore = taskDao.reminders.getValue("r1")

        repository.assignToColumn("pan", "columna-1")

        assertEquals(1, taskDao.writeCalls)
        assertEquals(0, taskDao.extrasCalls)
        assertEquals(subtaskBefore, taskDao.subtasks.getValue("s1"))
        assertEquals(reminderBefore, taskDao.reminders.getValue("r1"))
        assertTrue(taskDao.myDayEntries.isEmpty())
    }

    @Test
    fun assignToColumn_conColumnaLlena_noEscribe() = runTest {
        val (repository, taskDao) = extrasRepository(wipLimit = 1)
        taskDao.put(taskEntity("x", columnId = "columna-1", position = 0))
        taskDao.put(taskEntity("pan", columnId = null, position = 0))
        val writesBefore = taskDao.writeCalls

        val error = failureOf(repository.assignToColumn("pan", "columna-1"))

        assertEquals("COLUMN_WIP_LIMIT_REACHED", error.code)
        assertEquals(writesBefore, taskDao.writeCalls)
        assertNull(taskDao.stored.getValue("pan").columnId)
    }

    @Test
    fun assignToColumn_columnaInexistente_noEscribe() = runTest {
        val (repository, taskDao) = extrasRepository()
        taskDao.put(taskEntity("pan", columnId = null, position = 0))
        val writesBefore = taskDao.writeCalls

        val error = failureOf(repository.assignToColumn("pan", "columna-x"))

        assertEquals("COLUMN_NOT_FOUND", error.code)
        assertEquals(writesBefore, taskDao.writeCalls)
    }

    @Test
    fun assignToColumn_tareaInexistente_noEscribe() = runTest {
        val (repository, taskDao) = extrasRepository()

        val error = failureOf(repository.assignToColumn("nada", "columna-1"))

        assertEquals("TASK_NOT_FOUND", error.code)
        assertEquals(0, taskDao.writeCalls)
    }

    @Test
    fun assignToColumn_tareaConColumna_devuelveTaskNotInInbox() = runTest {
        val (repository, taskDao) = extrasRepository()
        taskDao.put(taskEntity("t0", columnId = "columna-1", position = 0))
        val writesBefore = taskDao.writeCalls

        val error = failureOf(repository.assignToColumn("t0", "columna-1"))

        assertEquals("TASK_NOT_IN_INBOX", error.code)
        assertEquals(writesBefore, taskDao.writeCalls)
    }

    @Test
    fun moveTask_deTareaDeBandeja_devuelveTaskInInboxSinEscribir() = runTest {
        val (repository, taskDao) = extrasRepository()
        taskDao.put(taskEntity("pan", columnId = null, position = 0))
        val writesBefore = taskDao.writeCalls

        val error = failureOf(repository.moveTask("pan", "columna-1", 0))

        assertEquals("TASK_IN_INBOX", error.code)
        assertEquals(writesBefore, taskDao.writeCalls)
    }

    @Test
    fun observeList_mapeaTareas() = runTest {
        val (repository, taskDao) = extrasRepository()
        taskDao.put(taskEntity("pan", columnId = null, position = 0))
        taskDao.put(taskEntity("borrada", columnId = null, position = 1, deletedAt = now))
        taskDao.put(taskEntity("otra-lista", columnId = null, position = 0, listId = "compras"))
        taskDao.put(taskEntity("t0", columnId = "columna-1", position = 0))

        assertEquals(listOf("pan"), repository.observeList(SEEDED_TASK_LIST_ID).first().map { it.id })
    }

    @Test
    fun observeActiveCountsByColumn_conviertaFilasEnMapa() = runTest {
        val (repository, taskDao) = extrasRepository()
        taskDao.put(taskEntity("a", columnId = "columna-1", position = 0))
        taskDao.put(taskEntity("b", columnId = "columna-1", position = 1))
        taskDao.put(taskEntity("pan", columnId = null, position = 0))

        assertEquals(mapOf("columna-1" to 2), repository.observeActiveCountsByColumn().first())
    }

    @Test
    fun activeTaskCount_incluyeBandeja() = runTest {
        val (repository, taskDao) = extrasRepository()
        taskDao.put(taskEntity("pan", columnId = null, position = 0, dueAt = DUE))
        taskDao.put(taskEntity("t0", columnId = "columna-1", position = 0))

        assertEquals(2, repository.activeTaskCount())
    }

    private fun subtaskEntity(id: String, taskId: String, deletedAt: Long? = null): SubtaskEntity =
        SubtaskEntity(
            id = id,
            taskId = taskId,
            title = "Subtarea $id",
            completed = false,
            position = 0,
            assigneeId = null,
            dueAt = null,
            createdAt = 0L,
            updatedAt = now.toEpochMilli(),
            pending = false,
            deletedAt = deletedAt,
        )

    private fun reminderEntity(id: String, taskId: String, status: String): ReminderEntity = ReminderEntity(
        id = id,
        taskId = taskId,
        remindAt = later.toEpochMilli(),
        status = status,
        sentAt = null,
        createdAt = 0L,
        updatedAt = now.toEpochMilli(),
        pending = false,
        deletedAt = null,
    )

    // Una tarea sin columna vive siempre en una lista; por defecto, «Tareas».
    private fun taskEntity(
        id: String,
        columnId: String?,
        position: Int,
        updatedAt: Instant = now,
        deletedAt: Instant? = null,
        dueAt: Instant? = null,
        createdAt: Long = 0L,
        completedAt: Instant? = null,
        listId: String? = if (columnId == null) SEEDED_TASK_LIST_ID else null,
    ): TaskEntity = TaskEntity(
        id = id,
        columnId = columnId,
        listId = listId,
        title = "Tarea $id",
        description = null,
        priority = "MEDIUM",
        dueAt = dueAt?.toEpochMilli(),
        assigneeId = null,
        position = position,
        createdAt = createdAt,
        updatedAt = updatedAt.toEpochMilli(),
        pending = true,
        completedAt = completedAt?.toEpochMilli(),
        deletedAt = deletedAt?.toEpochMilli(),
    )

    private fun listRepository(): Pair<TaskRepositoryImpl, FakeTaskDao> {
        val columnDao = FakeColumnDao().apply {
            put(columnEntity("columna-1"))
            put(columnEntity("done", position = 1, isDone = true))
        }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.listDao.put(listEntity("compras", "Compras", position = 1))
        taskDao.listDao.put(listEntity("viajes", "Viajes", position = 2))
        taskDao.listDao.put(listEntity("borrada", "Borrada", position = 3, deletedAt = 1L))
        return TaskRepositoryImpl(
            taskDao,
            columnDao,
            taskDao.subtaskDao,
            taskDao.reminderDao,
            taskDao.listDao,
            taskDao.defaultListId,
            now = { later },
            timeZone = { ZoneOffset.UTC },
        ) to taskDao
    }

    @Test
    fun createTask_sinDestino_vaALaListaPorDefectoActual() = runTest {
        val (repository, taskDao) = listRepository()

        val created = (repository.createTask(null, TaskDraft(title = "Pan")) as DomainResult.Success).value

        assertEquals(SEEDED_TASK_LIST_ID, created.listId)
        assertNull(created.columnId)
        assertEquals(SEEDED_TASK_LIST_ID, taskDao.stored.getValue(created.id).listId)
    }

    @Test
    fun createTask_sinDestino_trasBorrarTareas_vaALaOtraLista() = runTest {
        val (repository, taskDao) = listRepository()
        val tareas = taskDao.listDao.stored.getValue(SEEDED_TASK_LIST_ID)
        taskDao.listDao.put(tareas.copy(deletedAt = 1L))

        val created = (repository.createTask(null, TaskDraft(title = "Pan")) as DomainResult.Success).value
        val extras = (
            repository.createTaskWithExtras(null, TaskCreation(TaskDraft(title = "Leche"))) as DomainResult.Success
            ).value

        assertEquals("compras", created.listId)
        assertEquals("compras", extras.listId)
        assertEquals(1, taskDao.stored.getValue(extras.id).position)
    }

    @Test
    fun setCompleted_tareaRepetidaEnListaBorrada_siLaDefaultCambio_vaALaNueva() = runTest {
        val (repository, taskDao) = listRepository()
        taskDao.listDao.put(taskDao.listDao.stored.getValue(SEEDED_TASK_LIST_ID).copy(deletedAt = 1L))
        taskDao.put(repeating("a", null, 0).copy(listId = "borrada"))

        repository.setCompleted("a", true)

        assertEquals("compras", taskDao.successorOf("a").listId)
    }

    @Test
    fun createTask_conListId_guardaListIdYSinColumna() = runTest {
        val (repository, taskDao) = listRepository()
        taskDao.put(taskEntity("c0", columnId = null, position = 0, listId = "compras"))
        taskDao.put(taskEntity("c-borrada", columnId = null, position = 1, deletedAt = now, listId = "compras"))

        val created = (repository.createTask(null, TaskDraft(title = "Leche"), "compras") as DomainResult.Success).value

        val row = taskDao.stored.getValue(created.id)
        assertEquals("compras", row.listId)
        assertNull(row.columnId)
        assertEquals(1, row.position)
        assertTrue(row.pending)
    }

    @Test
    fun createTask_listaInexistente_devuelveNotFoundSinEscribir() = runTest {
        val (repository, taskDao) = listRepository()

        val error = failureOf(repository.createTask(null, TaskDraft(title = "Leche"), "no-existe"))

        assertTrue(error is DomainError.NotFound)
        assertEquals("LIST_NOT_FOUND", error.code)
        assertEquals(0, taskDao.writeCalls)
        assertTrue(taskDao.stored.isEmpty())
    }

    @Test
    fun createTask_listaBorrada_devuelveNotFound() = runTest {
        val (repository, taskDao) = listRepository()

        val error = failureOf(repository.createTask(null, TaskDraft(title = "Leche"), "borrada"))

        assertEquals("LIST_NOT_FOUND", error.code)
        assertTrue(taskDao.stored.isEmpty())
    }

    @Test
    fun createTask_columnaYLista_devuelveConflictSinEscribir() = runTest {
        val (repository, taskDao) = listRepository()

        val error = failureOf(repository.createTask("columna-1", TaskDraft(title = "Leche"), "compras"))

        assertTrue(error is DomainError.Validation)
        assertEquals("TASK_PLACEMENT_CONFLICT", error.code)
        assertEquals(0, taskDao.writeCalls)
        assertTrue(taskDao.stored.isEmpty())
        val extras = failureOf(
            repository.createTaskWithExtras("columna-1", TaskCreation(TaskDraft(title = "Leche")), "compras"),
        )
        assertEquals("TASK_PLACEMENT_CONFLICT", extras.code)
        assertEquals(0, taskDao.extrasCalls)
    }

    @Test
    fun createTaskWithExtras_enLista_noConsultaColumnas() = runTest {
        val (repository, taskDao) = listRepository()

        val created = (
            repository.createTaskWithExtras(null, TaskCreation(TaskDraft(title = "Leche")), "compras")
                as DomainResult.Success
            ).value

        assertEquals("compras", taskDao.stored.getValue(created.id).listId)
        assertEquals(0, taskDao.columnDao.findByIdCalls)
        assertEquals(0, taskDao.columnDao.findActiveByBoardCalls)
        assertEquals(0, taskDao.findActiveByColumnCalls)
        assertEquals(1, taskDao.extrasCalls)
    }

    @Test
    fun moveToList_cambiaListIdYPosicion() = runTest {
        val (repository, taskDao) = listRepository()
        taskDao.put(taskEntity("a", columnId = null, position = 0))
        taskDao.put(taskEntity("v0", columnId = null, position = 0, listId = "viajes"))
        taskDao.put(taskEntity("v1", columnId = null, position = 1, listId = "viajes"))

        val moved = (repository.moveToList("a", "viajes") as DomainResult.Success).value

        val row = taskDao.stored.getValue("a")
        assertEquals("viajes", moved.listId)
        assertEquals("viajes", row.listId)
        assertEquals(2, row.position)
        assertEquals(later.toEpochMilli(), row.updatedAt)
        assertTrue(row.pending)
        assertNull(row.columnId)
    }

    @Test
    fun moveToList_noCambiaCompletadaFechaNiMiDia() = runTest {
        val (repository, taskDao) = listRepository()
        val original = taskEntity("a", columnId = null, position = 0, dueAt = DUE, completedAt = now)
        taskDao.put(original)

        repository.moveToList("a", "compras")

        val row = taskDao.stored.getValue("a")
        assertEquals(original.completedAt, row.completedAt)
        assertEquals(original.dueAt, row.dueAt)
        assertEquals(original.title, row.title)
        assertTrue(taskDao.myDayEntries.isEmpty())
        assertEquals(0, taskDao.successorCalls)
    }

    @Test
    fun moveToList_mismaLista_noEscribe() = runTest {
        val (repository, taskDao) = listRepository()
        taskDao.put(taskEntity("a", columnId = null, position = 0, listId = "compras"))

        val result = repository.moveToList("a", "compras")

        assertTrue(result is DomainResult.Success)
        assertEquals(0, taskDao.writeCalls)
        assertEquals(0L, taskDao.stored.getValue("a").createdAt)
        assertEquals(now.toEpochMilli(), taskDao.stored.getValue("a").updatedAt)
    }

    @Test
    fun moveToList_tareaDeTablero_devuelveTaskInBoard() = runTest {
        val (repository, taskDao) = listRepository()
        taskDao.put(taskEntity("a", columnId = "columna-1", position = 0))

        val error = failureOf(repository.moveToList("a", "compras"))

        assertTrue(error is DomainError.Validation)
        assertEquals("TASK_IN_BOARD", error.code)
        assertEquals(0, taskDao.writeCalls)
        assertNull(taskDao.stored.getValue("a").listId)
    }

    @Test
    fun moveToList_listaInexistente_noEscribe() = runTest {
        val (repository, taskDao) = listRepository()
        taskDao.put(taskEntity("a", columnId = null, position = 0))

        listOf("no-existe", "borrada").forEach { destination ->
            val error = failureOf(repository.moveToList("a", destination))

            assertTrue(error is DomainError.NotFound)
            assertEquals("LIST_NOT_FOUND", error.code)
        }
        assertEquals(0, taskDao.writeCalls)
        assertEquals(SEEDED_TASK_LIST_ID, taskDao.stored.getValue("a").listId)
    }

    @Test
    fun moveToList_tareaInexistente_noEscribe() = runTest {
        val (repository, taskDao) = listRepository()
        taskDao.put(taskEntity("borrada", columnId = null, position = 0, deletedAt = now))

        listOf("no-existe", "borrada").forEach { id ->
            val error = failureOf(repository.moveToList(id, "compras"))

            assertTrue(error is DomainError.NotFound)
            assertEquals("TASK_NOT_FOUND", error.code)
        }
        assertEquals(0, taskDao.writeCalls)
    }

    @Test
    fun assignToColumn_desdeCualquierLista_vaciaListId() = runTest {
        val (repository, taskDao) = listRepository()
        taskDao.put(taskEntity("a", columnId = null, position = 0))
        taskDao.put(taskEntity("b", columnId = null, position = 0, listId = "compras"))

        repository.assignToColumn("a", "columna-1")
        repository.assignToColumn("b", "columna-1")

        listOf("a", "b").forEach { id ->
            assertNull(id, taskDao.stored.getValue(id).listId)
            assertEquals(id, "columna-1", taskDao.stored.getValue(id).columnId)
        }
    }

    @Test
    fun assignToColumn_desdeListaPropia_daColumnaYPosicionFinal() = runTest {
        val (repository, taskDao) = listRepository()
        taskDao.put(taskEntity("t0", columnId = "columna-1", position = 0))
        taskDao.put(taskEntity("t1", columnId = "columna-1", position = 1))
        taskDao.put(taskEntity("b", columnId = null, position = 0, listId = "compras"))

        val assigned = (repository.assignToColumn("b", "columna-1") as DomainResult.Success).value

        assertEquals("columna-1", assigned.columnId)
        assertNull(assigned.listId)
        assertEquals(2, taskDao.stored.getValue("b").position)
    }

    @Test
    fun deleteTask_enLista_noCambiaLaCascada() = runTest {
        val (repository, taskDao) = listRepository()
        taskDao.put(taskEntity("a", columnId = null, position = 0, listId = "compras"))
        taskDao.putSubtask(subtaskEntity("s1", taskId = "a"))
        taskDao.putReminder(reminderEntity("r1", "a", "PENDING"))

        assertTrue(repository.deleteTask("a") is DomainResult.Success)

        val row = taskDao.stored.getValue("a")
        assertEquals(later.toEpochMilli(), row.deletedAt)
        assertEquals("compras", row.listId)
        assertEquals(later.toEpochMilli(), taskDao.subtasks.getValue("s1").deletedAt)
        assertEquals("DISMISSED", taskDao.reminders.getValue("r1").status)
        assertEquals(1, taskDao.transactionalCalls)
    }

    @Test
    fun setCompleted_tareaRepetidaEnLista_laSiguienteQuedaEnLaMismaLista() = runTest {
        val (repository, taskDao) = listRepository()
        taskDao.put(repeating("a", null, 0).copy(listId = "compras"))
        taskDao.put(taskEntity("c1", columnId = null, position = 1, listId = "compras"))

        repository.setCompleted("a", true)

        val next = taskDao.successorOf("a")
        assertEquals("compras", next.listId)
        assertNull(next.columnId)
        assertEquals(2, next.position)
        assertEquals("compras", taskDao.stored.getValue("a").listId)
    }

    @Test
    fun assignToColumn_aColumnaDeCierre_tareaRepetidaDeLista_laSiguienteQuedaEnSuLista() = runTest {
        val (repository, taskDao) = listRepository()
        taskDao.put(repeating("a", null, 0).copy(listId = "compras"))
        taskDao.put(taskEntity("c1", columnId = null, position = 1, listId = "compras"))

        repository.assignToColumn("a", "done")

        val original = taskDao.stored.getValue("a")
        val next = taskDao.successorOf("a")
        assertEquals("done", original.columnId)
        assertNull(original.listId)
        assertEquals("compras", next.listId)
        assertNull(next.columnId)
        assertEquals(1, next.position)
    }

    @Test
    fun setCompleted_tareaRepetidaEnListaBorrada_laSiguienteVaALaListaPorDefectoActual() = runTest {
        val (repository, taskDao) = listRepository()
        taskDao.put(repeating("a", null, 0).copy(listId = "borrada"))
        taskDao.put(taskEntity("t0", columnId = null, position = 0))

        val result = repository.setCompleted("a", true)

        assertTrue(result is DomainResult.Success)
        val next = taskDao.successorOf("a")
        assertEquals(SEEDED_TASK_LIST_ID, next.listId)
        assertEquals(1, next.position)
    }

    @Test
    fun setCompleted_tareaRepetidaDeTablero_laSiguienteNoTieneListId() = runTest {
        val (repository, taskDao) = listRepository()
        taskDao.put(repeating("a", "columna-1", 0))

        repository.setCompleted("a", true)

        val next = taskDao.successorOf("a")
        assertNull(next.listId)
        assertEquals("done", taskDao.stored.getValue("a").columnId)
        assertEquals("columna-1", next.columnId)
    }

    private class FakeTaskDao(val columnDao: FakeColumnDao) : TaskDao() {
        override fun observeSearchable(): Flow<List<SearchTaskRow>> = emptyFlow()

        override suspend fun findActiveByList(listId: String): List<TaskEntity> =
            rows.value.values.filter { it.listId == listId && it.deletedAt == null }

        override suspend fun restoreSubtasksOfTask(taskId: String, deletedAt: Long, now: Long) {
            subtasks.replaceAll { _, row ->
                if (row.taskId == taskId && row.deletedAt == deletedAt) {
                    row.copy(deletedAt = null, updatedAt = now, pending = true)
                } else {
                    row
                }
            }
        }

        override suspend fun restoreRemindersOfTask(taskId: String, deletedAt: Long, now: Long): Int {
            val ids = reminders.values
                .filter { it.taskId == taskId && it.status == "DISMISSED" && it.updatedAt == deletedAt && it.remindAt > now }
                .map { it.id }
            ids.forEach { id -> reminders[id] = reminders.getValue(id).copy(status = "PENDING", updatedAt = now, pending = true) }
            return ids.size
        }

        var restoreCalls: Int = 0
            private set

        override suspend fun upsertRestoredWithSubtasks(tasks: List<TaskEntity>, deletedAtById: Map<String, Long>, now: Long) {
            restoreCalls++
            super.upsertRestoredWithSubtasks(tasks, deletedAtById, now)
        }
        val store = FakeTaskStore()

        val listDao = FakeTaskListDao(store)

        val defaultListId: suspend () -> String = {
            defaultListOf(listDao.stored.values.map { it.toTaskList() }).let { checkNotNull(it).id }
        }

        override fun observeByList(listId: String): Flow<List<TaskEntity>> = rows.map { storedRows ->
            storedRows.values
                .filter { it.listId == listId && it.deletedAt == null }
                .sortedWith(compareByDescending<TaskEntity> { it.createdAt }.thenBy { it.id })
        }

        var countInListCalls: Int = 0
            private set

        override suspend fun countInList(listId: String): Int {
            countInListCalls++
            return rows.value.values.count { it.listId == listId && it.deletedAt == null }
        }

        override fun observeActiveCountsByList(): Flow<List<ListCountRow>> = rows.map { storedRows ->
            storedRows.values
                .filter { it.deletedAt == null && it.completedAt == null && it.listId != null }
                .groupBy { requireNotNull(it.listId) }
                .map { (listId, tasks) -> ListCountRow(listId, tasks.size) }
        }

        override fun observeCountsByColumn(): Flow<List<ColumnCountRow>> = rows.map { storedRows ->
            storedRows.values
                .filter { it.deletedAt == null && it.columnId != null }
                .groupBy { requireNotNull(it.columnId) }
                .map { (columnId, tasks) -> ColumnCountRow(columnId, tasks.size) }
        }

        val myDayEntries: MutableMap<String, MyDayEntryEntity> = mutableMapOf()

        override suspend fun insertMyDayEntry(entry: MyDayEntryEntity) {
            myDayEntries[entry.id] = entry
        }

        private val rows get() = store.rows

        var writeCalls: Int = 0
            private set

        val stored: Map<String, TaskEntity> get() = rows.value

        fun put(task: TaskEntity) = store.put(task)

        override fun observeByBoard(boardId: String): Flow<List<TaskEntity>> = rows.map { storedRows ->
            val activeColumns = columnDao.activeColumnIds(boardId)
            storedRows.values
                .filter { it.columnId in activeColumns && it.deletedAt == null }
                .sortedWith(compareBy({ it.position }, { it.id }))
        }

        var findActiveByColumnCalls: Int = 0
            private set

        override suspend fun findActiveByColumn(columnId: String): List<TaskEntity> {
            findActiveByColumnCalls++
            return activeByColumn(columnId)
        }

        private fun activeByColumn(columnId: String): List<TaskEntity> =
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

        override suspend fun upsertAll(tasks: List<TaskEntity>) {
            writeCalls++
            rows.value = rows.value + tasks.associateBy { it.id }
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

        val reminders: MutableMap<String, ReminderEntity> = mutableMapOf()

        val subtaskDao: SubtaskDao = object : SubtaskDao() {
            override fun observeByTask(taskId: String): Flow<List<SubtaskEntity>> = error("no se usa")

            override suspend fun findActiveByTask(taskId: String): List<SubtaskEntity> =
                subtasks.values
                    .filter { it.taskId == taskId && it.deletedAt == null }
                    .sortedWith(compareBy({ it.position }, { it.id }))

            override suspend fun findById(id: String): SubtaskEntity? = subtasks[id]

            override fun observeTotalCount(taskId: String): Flow<Int> = error("no se usa")

            override fun observeCompletedCount(taskId: String): Flow<Int> = error("no se usa")

            override suspend fun upsert(subtask: SubtaskEntity) {
                subtasks[subtask.id] = subtask
            }
        }

        val reminderDao: ReminderDao = object : ReminderDao() {
            override fun observePendingByTask(taskId: String): Flow<ReminderEntity?> = error("no se usa")

            override fun observeAllPending(): Flow<List<ReminderEntity>> = error("no se usa")

            override suspend fun findPendingByTask(taskId: String): ReminderEntity? = error("no se usa")

            override suspend fun findLatestByTask(taskId: String): ReminderEntity? =
                reminders.values.filter { it.taskId == taskId && it.deletedAt == null }.maxByOrNull { it.createdAt }

            override suspend fun findById(id: String): ReminderEntity? = reminders[id]

            override suspend fun findPendingUpTo(limit: Long): List<ReminderEntity> = error("no se usa")

            override suspend fun minPendingAfter(after: Long): Long? = error("no se usa")

            override suspend fun upsert(reminder: ReminderEntity) {
                reminders[reminder.id] = reminder
            }

            override suspend fun markSent(id: String, now: Long): Int = error("no se usa")

            override suspend fun markDismissed(id: String, now: Long): Int = error("no se usa")
        }

        var successorCalls: Int = 0
            private set

        override suspend fun upsertAllWithSuccessor(
            tasks: List<TaskEntity>,
            successor: TaskEntity,
            subtasks: List<SubtaskEntity>,
            reminder: ReminderEntity?,
        ) {
            successorCalls++
            super.upsertAllWithSuccessor(tasks, successor, subtasks, reminder)
        }

        fun putReminder(reminder: ReminderEntity) {
            reminders[reminder.id] = reminder
        }

        override fun observeReminderCandidates(): Flow<List<ReminderCandidateRow>> = rows.map {
            activeTasks().filter { task -> task.dueAt != null }
                .map { task -> ReminderCandidateRow(task.id, task.title, requireNotNull(task.dueAt)) }
        }

        override fun observeCompletedCount(): Flow<Int> = rows.map { stored ->
            stored.values.count { it.completedAt != null && it.deletedAt == null }
        }

        override suspend fun findCompleted(): List<TaskEntity> =
            rows.value.values.filter { it.completedAt != null && it.deletedAt == null }.sortedBy { it.id }

        override suspend fun countActive(): Int = activeTasks().size

        override suspend fun findReminderState(taskId: String): ReminderTaskStateRow? =
            rows.value[taskId]?.let {
                ReminderTaskStateRow(it.title, it.deletedAt != null, it.completedAt != null)
            }

        private fun activeTasks(): List<TaskEntity> =
            rows.value.values.filter { it.deletedAt == null && it.completedAt == null }

        override suspend fun dismissRemindersOfTask(taskId: String, now: Long): Int {
            val pendingIds = reminders.values.filter { it.taskId == taskId && it.status == "PENDING" }.map { it.id }
            pendingIds.forEach { id ->
                reminders[id] = reminders.getValue(id).copy(status = "DISMISSED", updatedAt = now, pending = true)
            }
            return pendingIds.size
        }

        override suspend fun insertSubtasks(subtasks: List<SubtaskEntity>) {
            subtasks.forEach { this.subtasks[it.id] = it }
        }

        override suspend fun insertReminder(reminder: ReminderEntity) {
            reminders[reminder.id] = reminder
        }

        var extrasCalls: Int = 0
            private set

        override suspend fun insertTaskWithExtras(
            task: TaskEntity,
            subtasks: List<SubtaskEntity>,
            reminder: ReminderEntity?,
            myDayEntry: MyDayEntryEntity?,
        ) {
            extrasCalls++
            inTransaction = true
            try {
                super.insertTaskWithExtras(task, subtasks, reminder, myDayEntry)
            } finally {
                inTransaction = false
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
        override fun observeAllActive(): Flow<List<ColumnEntity>> = emptyFlow()

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

        var findActiveByBoardCalls: Int = 0
            private set

        override suspend fun findActiveByBoard(boardId: String): List<ColumnEntity> {
            findActiveByBoardCalls++
            return rows.value.values.filter { it.boardId == boardId && it.deletedAt == null }
        }

        var findByIdCalls: Int = 0
            private set

        override suspend fun findById(id: String): ColumnEntity? {
            findByIdCalls++
            return rows.value[id]
        }

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
        val DUE: Instant = Instant.parse("2026-11-01T12:00:00Z")
        val REPEAT_DUE: Instant = Instant.parse("2026-10-21T09:00:00Z")
    }
}
