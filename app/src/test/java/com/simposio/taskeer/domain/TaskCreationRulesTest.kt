package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.NewTaskBundle
import com.simposio.taskeer.domain.model.ReminderStatus
import com.simposio.taskeer.domain.model.SubtaskDraft
import com.simposio.taskeer.domain.model.TaskCreation
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.task.newTaskWithExtras
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCreationRulesTest {

    private val now: Instant = Instant.parse("2026-10-21T10:00:00Z")
    private val remindAt: Instant = now.plusSeconds(3600)

    @Test
    fun loteCompleto_compartetaskIdPositionPorIndiceYNow() {
        val dueAt = now.plusSeconds(7200)
        val creation = TaskCreation(
            task = TaskDraft(title = "Informe"),
            subtasks = listOf(
                SubtaskDraft("  Datos  ", assigneeId = "  ", dueAt = dueAt),
                SubtaskDraft("Borrador", assigneeId = "yo"),
            ),
            remindAt = remindAt,
        )

        val bundle = success(newTaskWithExtras("c1", creation, position = 4, now = now, clientId = "t-1"))

        assertEquals("t-1", bundle.task.id)
        assertEquals(4, bundle.task.position)
        assertEquals(listOf("t-1", "t-1"), bundle.subtasks.map { it.taskId })
        assertEquals(listOf(0, 1), bundle.subtasks.map { it.position })
        assertEquals(listOf("Datos", "Borrador"), bundle.subtasks.map { it.title })
        assertEquals(listOf(null, "yo"), bundle.subtasks.map { it.assigneeId })
        assertEquals(dueAt, bundle.subtasks[0].dueAt)
        assertEquals(listOf(false, false), bundle.subtasks.map { it.completed })
        assertTrue(bundle.subtasks.all { it.sync.pending && it.sync.updatedAt == now })
        assertEquals(2, bundle.subtasks.map { it.id }.toSet().size)
        assertEquals("t-1", bundle.reminder?.taskId)
    }

    @Test
    fun subtareaInvalida_devuelveElPrimerFallo() {
        val creation = TaskCreation(
            task = TaskDraft(title = "Informe"),
            subtasks = listOf(
                SubtaskDraft("Bien"),
                SubtaskDraft("x".repeat(201)),
                SubtaskDraft("   "),
            ),
        )

        val error = failure(newTaskWithExtras("c1", creation, position = 0, now = now))

        assertEquals("SUBTASK_TITLE_TOO_LONG", error)
    }

    @Test
    fun recordatorioPasado_falla() {
        val creation = TaskCreation(TaskDraft(title = "Informe"), remindAt = now)

        val result = newTaskWithExtras("c1", creation, position = 0, now = now)

        assertEquals("VALIDATION_ERROR", failure(result))
    }

    @Test
    fun recordatorioFuturo_naceEnPending() {
        val creation = TaskCreation(TaskDraft(title = "Informe"), remindAt = remindAt)

        val reminder = success(newTaskWithExtras("c1", creation, position = 0, now = now)).reminder

        assertEquals(ReminderStatus.PENDING, reminder?.status)
        assertEquals(remindAt, reminder?.remindAt)
        assertNull(reminder?.sentAt)
        assertTrue(reminder?.sync?.pending == true)
    }

    @Test
    fun sinExtras_noGeneraSubtareasNiRecordatorio() {
        val bundle = success(newTaskWithExtras("c1", TaskCreation(TaskDraft(title = "Informe")), 0, now))

        assertTrue(bundle.subtasks.isEmpty())
        assertNull(bundle.reminder)
    }

    @Test
    fun tituloDeTareaInvalido_fallaAntesQueLosHijos() {
        val creation = TaskCreation(
            task = TaskDraft(title = "  "),
            subtasks = listOf(SubtaskDraft("x".repeat(201))),
            remindAt = now,
        )

        val error = failure(newTaskWithExtras("c1", creation, position = 0, now = now))

        assertEquals("TASK_TITLE_REQUIRED", error)
    }

    private fun success(result: DomainResult<NewTaskBundle>): NewTaskBundle =
        (result as DomainResult.Success).value

    private fun failure(result: DomainResult<NewTaskBundle>): String =
        (result as DomainResult.Failure).error.code
}
