package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.task.newTask
import com.simposio.taskeer.domain.task.updated
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRulesTest {

    private val now: Instant = Instant.parse("2026-10-21T10:00:00Z")
    private val later: Instant = now.plusSeconds(60)

    @Test
    fun task_exponeIdYEstadoDesdeLosMetadatos() {
        val task = task(columnId = "columna-1", sync = SyncMetadata("tarea-1", now, pending = true, deletedAt = null))

        assertEquals("tarea-1", task.id)
        assertFalse(task.isDeleted)

        val deleted = task.copy(sync = task.sync.copy(deletedAt = later))

        assertEquals("tarea-1", deleted.id)
        assertTrue(deleted.isDeleted)
    }

    @Test
    fun prioridad_esUnConjuntoCerradoDeCuatroValores() {
        assertEquals(
            listOf("LOW", "MEDIUM", "HIGH", "URGENT"),
            TaskPriority.entries.map { it.name },
        )
        assertEquals(4, TaskPriority.entries.size)
        assertEquals(TaskPriority.MEDIUM, TaskDraft(title = "T").priority)
    }

    @Test
    fun prioridad_desconocidaEnAlmacenamiento_degradaAMedium() {
        assertEquals(TaskPriority.MEDIUM, TaskPriority.fromStorage("MEDIUM"))
        assertEquals(TaskPriority.URGENT, TaskPriority.fromStorage("URGENT"))
        assertEquals(TaskPriority.MEDIUM, TaskPriority.fromStorage("BLOCKER"))
        assertEquals(TaskPriority.MEDIUM, TaskPriority.fromStorage("medium"))
    }

    @Test
    fun newTask_recortaElTituloYNormalizaLosCamposOpcionales() {
        val draft = TaskDraft(
            title = "  Escribir el informe  ",
            description = "   ",
            priority = TaskPriority.HIGH,
            assigneeId = "  ",
        )

        val created = (newTask("columna-1", draft, position = 0, now = now) as DomainResult.Success).value

        assertEquals("Escribir el informe", created.title)
        assertNull("una descripción en blanco se guarda como null", created.description)
        assertNull("un asignado en blanco se guarda como null", created.assigneeId)
        assertEquals(TaskPriority.HIGH, created.priority)
    }

    @Test
    fun newTask_nacePendienteYSinBorrar() {
        val created = (newTask("columna-1", TaskDraft(title = "Tarea"), 0, now, clientId = "tarea-1") as DomainResult.Success).value

        assertEquals("tarea-1", created.id)
        assertEquals(now, created.sync.updatedAt)
        assertTrue(created.sync.pending)
        assertNull(created.sync.deletedAt)
        assertFalse(created.isDeleted)
    }

    @Test
    fun newTask_conservaElDueAtYLaPosicion() {
        val dueAt = Instant.parse("2026-10-22T08:30:00Z")

        val created = (newTask("columna-1", TaskDraft(title = "Tarea", dueAt = dueAt), 7, now) as DomainResult.Success).value

        assertEquals(dueAt, created.dueAt)
        assertEquals(7, created.position)
        assertEquals("columna-1", created.columnId)
    }

    @Test
    fun newTask_tituloVacio_fallaConValidation() {
        val result = newTask("columna-1", TaskDraft(title = "   "), 0, now)

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.Validation)
        assertEquals("TASK_TITLE_REQUIRED", error.code)
    }

    @Test
    fun updated_cambiaLosCamposEditablesYNoTocaIdColumnaNiPosicion() {
        val current = task(
            columnId = "columna-1",
            title = "Título viejo",
            position = 3,
            sync = SyncMetadata("tarea-1", now, pending = false, deletedAt = null),
        )
        val dueAt = Instant.parse("2026-10-23T09:00:00Z")
        val draft = TaskDraft(
            title = "  Título nuevo ",
            description = "  Detalle  ",
            priority = TaskPriority.URGENT,
            dueAt = dueAt,
            assigneeId = LocalUser.ID,
        )

        val updated = (current.updated(draft, later) as DomainResult.Success).value

        assertEquals("Título nuevo", updated.title)
        assertEquals("Detalle", updated.description)
        assertEquals(TaskPriority.URGENT, updated.priority)
        assertEquals(dueAt, updated.dueAt)
        assertEquals(LocalUser.ID, updated.assigneeId)
        assertEquals("tarea-1", updated.id)
        assertEquals("columna-1", updated.columnId)
        assertEquals(3, updated.position)
        assertNull(updated.sync.deletedAt)
    }

    @Test
    fun updated_marcaPendienteYActualizaUpdatedAt() {
        val current = task(sync = SyncMetadata("tarea-1", now, pending = false, deletedAt = null))

        val updated = (current.updated(TaskDraft(title = "Tarea"), later) as DomainResult.Success).value

        assertEquals(later, updated.sync.updatedAt)
        assertTrue(updated.sync.pending)
    }

    @Test
    fun updated_tituloVacio_fallaConValidation() {
        val current = task(sync = SyncMetadata("tarea-1", now, pending = false, deletedAt = null))

        val result = current.updated(TaskDraft(title = "  "), later)

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.Validation)
        assertEquals("TASK_TITLE_REQUIRED", error.code)
    }

    private fun task(
        columnId: String = "columna-1",
        title: String = "Tarea",
        position: Int = 0,
        sync: SyncMetadata = SyncMetadata("tarea-1", now, pending = true, deletedAt = null),
    ): Task = Task(
        columnId = columnId,
        title = title,
        description = null,
        priority = TaskPriority.MEDIUM,
        dueAt = null,
        assigneeId = null,
        position = position,
        sync = sync,
    )
}
