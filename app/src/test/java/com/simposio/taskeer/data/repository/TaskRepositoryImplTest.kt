package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.BoardEntity
import com.simposio.taskeer.data.local.ColumnDao
import com.simposio.taskeer.data.local.ColumnEntity
import com.simposio.taskeer.data.local.ReminderCandidateRow
import com.simposio.taskeer.data.local.ReminderEntity
import com.simposio.taskeer.data.local.ReminderTaskStateRow
import com.simposio.taskeer.data.local.SubtaskEntity
import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.data.local.toReminder
import com.simposio.taskeer.data.local.toTask
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.model.ReminderStatus
import com.simposio.taskeer.domain.model.SubtaskDraft
import com.simposio.taskeer.domain.model.TaskCreation
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.model.TaskPriority
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    fun createTask_sinAsignado_persisteLocalUser() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { now })

        val created = (repository.createTask("columna-1", TaskDraft(title = "Tarea")) as DomainResult.Success).value

        assertEquals(LocalUser.ID, taskDao.stored.getValue(created.id).assigneeId)
    }

    @Test
    fun updateTask_sinAsignado_persisteLocalUser() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0, updatedAt = now))
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { later })

        repository.updateTask("t1", TaskDraft(title = "Tarea", assigneeId = null))

        assertEquals(LocalUser.ID, taskDao.stored.getValue("t1").assigneeId)
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
    fun createTaskWithExtras_persisteTareaSubtareasYRecordatorioEnUnaLlamada() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t0", columnId = "columna-1", position = 0))
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { now })
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
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { now })

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
        return TaskRepositoryImpl(taskDao, columnDao, now = { now }) to taskDao
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
    fun deleteTask_descartaLosRecordatoriosPendientesDeLaTareaEnLaMismaTransaccion() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t1", columnId = "columna-1", position = 0))
        taskDao.putReminder(reminderEntity("r1", taskId = "t1", status = "PENDING"))
        taskDao.putReminder(reminderEntity("r2", taskId = "t1", status = "SENT"))
        taskDao.putReminder(reminderEntity("r3", taskId = "t2", status = "PENDING"))
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { later })

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
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { later })

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
            put(taskEntity("completada", columnId = "columna-hecha", position = 0, dueAt = DUE))
        }
        return TaskRepositoryImpl(taskDao, columnDao, now = { later })
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
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { later })

        repository.moveTask("a", "columna-2", 0)

        assertEquals(listOf("b" to 0, "c" to 1), taskDao.column("columna-1"))
        assertEquals(listOf("a" to 0, "x" to 1), taskDao.column("columna-2"))
    }

    @Test
    fun moveTask_loAlmacenadoEsLoQueObservaObserveTasksTrasReiniciar() = runTest {
        val (taskDao, repository) = moveFixture()
        repository.moveTask("a", "columna-2", 0)

        val reopened = TaskRepositoryImpl(taskDao, taskDao.columnDao, now = { later })
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
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { now })
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
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { now })

        val result = repository.createTask("columna-1", TaskDraft(title = "Nueva"))

        assertEquals(1, (result as DomainResult.Success).value.position)
    }

    @Test
    fun createTask_sinLimite_creaAunqueHayaMuchas() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        repeat(5) { taskDao.put(taskEntity("t$it", columnId = "columna-1", position = it)) }
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { now })

        assertTrue(repository.createTask("columna-1", TaskDraft(title = "Nueva")) is DomainResult.Success)
    }

    @Test
    fun createTask_tareaBorradaLiberaCupo() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1", wipLimit = 1)) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t0", columnId = "columna-1", position = 0, deletedAt = now))
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { now })

        assertTrue(repository.createTask("columna-1", TaskDraft(title = "Nueva")) is DomainResult.Success)
    }

    @Test
    fun createTask_tituloVacioEnColumnaLlena_devuelveTituloRequerido() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1", wipLimit = 1)) }
        val taskDao = FakeTaskDao(columnDao)
        taskDao.put(taskEntity("t0", columnId = "columna-1", position = 0))
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { now })

        val result = repository.createTask("columna-1", TaskDraft(title = "  "))

        assertEquals("TASK_TITLE_REQUIRED", (result as DomainResult.Failure).error.code)
    }

    @Test
    fun createTask_columnaInexistenteConLimite_devuelveColumnNotFound() = runTest {
        val columnDao = FakeColumnDao()
        val repository = TaskRepositoryImpl(FakeTaskDao(columnDao), columnDao, now = { now })

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
        return taskDao to TaskRepositoryImpl(taskDao, columnDao, now = { later })
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
    ): ColumnEntity = ColumnEntity(
        id = id,
        boardId = boardId,
        name = "Por hacer",
        position = 0,
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
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { now })

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
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { now })

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
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { later })

        val result = repository.updateTask("t1", TaskDraft(title = "x".repeat(201)))

        assertEquals("TASK_TITLE_TOO_LONG", (result as DomainResult.Failure).error.code)
        assertEquals(writesBefore, taskDao.writeCalls)
    }

    @Test
    fun createTask_fijaCreatedAtYUpdateTaskLoConserva() = runTest {
        val columnDao = FakeColumnDao().apply { put(columnEntity("columna-1")) }
        val taskDao = FakeTaskDao(columnDao)
        var clock = now
        val repository = TaskRepositoryImpl(taskDao, columnDao, now = { clock })

        val created = (repository.createTask("columna-1", TaskDraft(title = "T")) as DomainResult.Success).value
        clock = later
        repository.updateTask(created.id, TaskDraft(title = "U"))

        val row = taskDao.stored.getValue(created.id)
        assertEquals(now.toEpochMilli(), row.createdAt)
        assertEquals(later.toEpochMilli(), row.updatedAt)
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

    private fun taskEntity(
        id: String,
        columnId: String,
        position: Int,
        updatedAt: Instant = now,
        deletedAt: Instant? = null,
        dueAt: Instant? = null,
    ): TaskEntity = TaskEntity(
        id = id,
        columnId = columnId,
        title = "Tarea $id",
        description = null,
        priority = "MEDIUM",
        dueAt = dueAt?.toEpochMilli(),
        assigneeId = null,
        position = position,
        createdAt = 0L,
        updatedAt = updatedAt.toEpochMilli(),
        pending = true,
        deletedAt = deletedAt?.toEpochMilli(),
    )

    private class FakeTaskDao(val columnDao: FakeColumnDao) : TaskDao() {
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

        fun putReminder(reminder: ReminderEntity) {
            reminders[reminder.id] = reminder
        }

        override fun observeReminderCandidates(): Flow<List<ReminderCandidateRow>> = rows.map {
            activeTasks().filter { task -> task.dueAt != null }
                .map { task -> ReminderCandidateRow(task.id, task.title, requireNotNull(task.dueAt)) }
        }

        override suspend fun countActive(): Int = activeTasks().size

        override suspend fun findReminderState(taskId: String): ReminderTaskStateRow? =
            rows.value[taskId]?.let {
                ReminderTaskStateRow(it.title, it.deletedAt != null, columnDao.isDone(it.columnId))
            }

        private fun activeTasks(): List<TaskEntity> =
            rows.value.values.filter { it.deletedAt == null && !columnDao.isDone(it.columnId) }

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
        ) {
            extrasCalls++
            inTransaction = true
            try {
                super.insertTaskWithExtras(task, subtasks, reminder)
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
        private val rows = MutableStateFlow<Map<String, ColumnEntity>>(emptyMap())

        fun put(column: ColumnEntity) {
            rows.value = rows.value + (column.id to column)
        }

        fun isDone(columnId: String): Boolean = rows.value[columnId]?.isDoneColumn ?: false

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
        val DUE: Instant = Instant.parse("2026-11-01T12:00:00Z")
    }
}
