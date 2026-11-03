package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.Subtask
import com.simposio.taskeer.domain.model.SubtaskDraft
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.subtask.newSubtask
import com.simposio.taskeer.domain.subtask.softDeleted
import com.simposio.taskeer.domain.subtask.updated
import com.simposio.taskeer.domain.subtask.withCompleted
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtaskRulesTest {

    private val now: Instant = Instant.parse("2026-10-21T10:00:00Z")
    private val later: Instant = now.plusSeconds(60)

    @Test
    fun subtask_exponeIdYEstadoDesdeLosMetadatos() {
        val subtask = subtask()

        assertEquals("sub-1", subtask.id)
        assertFalse(subtask.isDeleted)
        assertTrue(subtask.copy(sync = subtask.sync.copy(deletedAt = later)).isDeleted)
    }

    @Test
    fun newSubtask_recortaTituloYNacePendienteSinCompletar() {
        val result = newSubtask("t1", SubtaskDraft("  Reunir datos  "), position = 3, now = now, clientId = "sub-9")

        val created = (result as DomainResult.Success).value
        assertEquals("Reunir datos", created.title)
        assertEquals("t1", created.taskId)
        assertFalse(created.completed)
        assertEquals(3, created.position)
        assertEquals("sub-9", created.id)
        assertEquals(now, created.sync.updatedAt)
        assertTrue(created.sync.pending)
        assertNull(created.sync.deletedAt)
    }

    @Test
    fun newSubtask_fijaCreatedAtConElNow() {
        val created = (newSubtask("t1", SubtaskDraft("A"), 0, now) as DomainResult.Success).value

        assertEquals(now, created.createdAt)
    }

    @Test
    fun newSubtask_guardaAsignadoRecortadoYFechaUtc() {
        val due = now.plusSeconds(120)

        val created = (newSubtask("t1", SubtaskDraft("A", "  local-user ", due), 0, now) as DomainResult.Success).value

        assertEquals("local-user", created.assigneeId)
        assertEquals(due, created.dueAt)
    }

    @Test
    fun newSubtask_asignadoEnBlanco_seGuardaNulo() {
        val created = (newSubtask("t1", SubtaskDraft("A", "   "), 0, now) as DomainResult.Success).value

        assertNull(created.assigneeId)
        assertNull(created.dueAt)
    }

    @Test
    fun newSubtask_sinTitulo_fallaConValidation() {
        val error = (newSubtask("t1", SubtaskDraft("   "), 0, now) as DomainResult.Failure).error

        assertTrue(error is DomainError.Validation)
        assertEquals("SUBTASK_TITLE_REQUIRED", error.code)
    }

    @Test
    fun titulo_longitudesDelLimite() {
        fun code(length: Int): String? =
            (newSubtask("t1", SubtaskDraft("x".repeat(length)), 0, now) as? DomainResult.Failure)?.error?.code

        assertEquals("SUBTASK_TITLE_REQUIRED", code(0))
        assertNull(code(1))
        assertNull(code(200))
        assertEquals("SUBTASK_TITLE_TOO_LONG", code(201))
    }

    @Test
    fun titulo_demasiadoLargo_mensajeEnEspanolConElLimite() {
        val error = (newSubtask("t1", SubtaskDraft("x".repeat(201)), 0, now) as DomainResult.Failure).error

        assertTrue(error.message.contains("200"))
        assertTrue(error.message.contains("subtarea"))
    }

    @Test
    fun updated_titulo201_fallaConTooLong() {
        val error = (subtask().updated(SubtaskDraft("x".repeat(201)), later) as DomainResult.Failure).error

        assertEquals("SUBTASK_TITLE_TOO_LONG", error.code)
    }

    @Test
    fun updated_cambiaTituloYNoTocaElResto() {
        val original = subtask().copy(completed = true, position = 2)

        val updated = (original.updated(SubtaskDraft("  Nuevo  "), later) as DomainResult.Success).value

        assertEquals("Nuevo", updated.title)
        assertEquals(original.copy(title = "Nuevo", sync = original.sync.copy(updatedAt = later)), updated)
        assertTrue(updated.sync.pending)
    }

    @Test
    fun updated_reemplazaAsignadoYFecha_yNuloLosQuita() {
        val due = now.plusSeconds(60)
        val withFields = (subtask().updated(SubtaskDraft("A", "local-user", due), later) as DomainResult.Success).value
        assertEquals("local-user", withFields.assigneeId)
        assertEquals(due, withFields.dueAt)

        val cleared = (withFields.updated(SubtaskDraft("A"), later) as DomainResult.Success).value

        assertNull(cleared.assigneeId)
        assertNull(cleared.dueAt)
    }

    @Test
    fun updated_conservaCreatedAtConOtroNow() {
        val original = subtask()

        val updated = (original.updated(SubtaskDraft("B"), later.plusSeconds(500)) as DomainResult.Success).value

        assertEquals(original.createdAt, updated.createdAt)
    }

    @Test
    fun updated_sinTitulo_fallaConValidation() {
        val error = (subtask().updated(SubtaskDraft(""), later) as DomainResult.Failure).error

        assertEquals("SUBTASK_TITLE_REQUIRED", error.code)
    }

    @Test
    fun withCompleted_conservaAsignadoFechaYCreatedAt() {
        val original = subtask().copy(assigneeId = "local-user", dueAt = now, createdAt = now.minusSeconds(9))

        val done = original.withCompleted(true, later)

        assertEquals("local-user", done.assigneeId)
        assertEquals(now, done.dueAt)
        assertEquals(original.createdAt, done.createdAt)
        assertEquals(original.createdAt, original.softDeleted(later).createdAt)
    }

    @Test
    fun withCompleted_cambiaCompletedYMarcaPendiente() {
        val original = subtask().copy(sync = subtask().sync.copy(pending = false))

        val done = original.withCompleted(true, later)

        assertTrue(done.completed)
        assertEquals(later, done.sync.updatedAt)
        assertTrue(done.sync.pending)
        assertEquals(original.title, done.title)
        assertEquals(original.position, done.position)
    }

    @Test
    fun softDeleted_marcaDeletedAtUpdatedAtYPending() {
        val deleted = subtask().softDeleted(later)

        assertEquals(later, deleted.sync.deletedAt)
        assertEquals(later, deleted.sync.updatedAt)
        assertTrue(deleted.sync.pending)
    }

    private fun subtask(): Subtask = Subtask(
        taskId = "t1",
        title = "Subtarea",
        completed = false,
        position = 0,
        createdAt = now,
        sync = SyncMetadata("sub-1", now, pending = true, deletedAt = null),
    )
}
