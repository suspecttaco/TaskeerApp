package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.task.checkSameBoard
import com.simposio.taskeer.domain.task.movedTask
import com.simposio.taskeer.domain.task.newTask
import com.simposio.taskeer.domain.task.softDeleted
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

    @Test
    fun newTask_longitudesDelTitulo() {
        fun code(length: Int): String? =
            (newTask("c", TaskDraft(title = "x".repeat(length)), 0, now) as? DomainResult.Failure)?.error?.code

        assertEquals("TASK_TITLE_REQUIRED", code(0))
        assertNull(code(200))
        assertEquals("TASK_TITLE_TOO_LONG", code(201))
    }

    @Test
    fun newTask_longitudesDeLaDescripcion() {
        fun code(length: Int): String? =
            (newTask("c", TaskDraft(title = "T", description = "d".repeat(length)), 0, now) as? DomainResult.Failure)
                ?.error?.code

        assertNull(code(5000))
        assertEquals("TASK_DESCRIPTION_TOO_LONG", code(5001))
    }

    @Test
    fun newTask_descripcionNulaOEnBlanco_sigueNormalizandoANulo() {
        listOf(null, "   ").forEach { description ->
            val created = (newTask("c", TaskDraft(title = "T", description = description), 0, now) as DomainResult.Success).value

            assertNull(created.description)
        }
    }

    @Test
    fun newTask_tituloYDescripcionInvalidos_prevaleceElDelTitulo() {
        val draft = TaskDraft(title = "x".repeat(201), description = "d".repeat(5001))

        val error = (newTask("c", draft, 0, now) as DomainResult.Failure).error

        assertEquals("TASK_TITLE_TOO_LONG", error.code)
    }

    @Test
    fun updated_aplicaLosMismosLimitesQueElAlta() {
        val current = task()

        val title = (current.updated(TaskDraft(title = "x".repeat(201)), later) as DomainResult.Failure).error
        val description = (current.updated(TaskDraft(title = "T", description = "d".repeat(5001)), later) as DomainResult.Failure).error

        assertEquals("TASK_TITLE_TOO_LONG", title.code)
        assertEquals("El título de la tarea no puede superar los 200 caracteres", title.message)
        assertEquals("TASK_DESCRIPTION_TOO_LONG", description.code)
        assertTrue(description.message.contains("5000"))
    }

    @Test
    fun createdAt_alCrearEsElNowYSobreviveAEditarOBorrar() {
        val created = (newTask("c", TaskDraft(title = "T"), 0, now) as DomainResult.Success).value
        assertEquals(now, created.createdAt)

        val otherNow = later.plusSeconds(500)
        val updated = (created.updated(TaskDraft(title = "U"), otherNow) as DomainResult.Success).value

        assertEquals(now, updated.createdAt)
        assertEquals(now, updated.softDeleted(otherNow).createdAt)
    }

    @Test
    fun movedTask_entreColumnas_renumeraOrigenYDestino() {
        val a = tk("a", "col-1", 0); val b = tk("b", "col-1", 1); val c = tk("c", "col-1", 2)
        val x = tk("x", "col-2", 0); val y = tk("y", "col-2", 1)

        val changed = ok(movedTask(a, listOf(a, b, c), listOf(x, y), "col-2", 1, later))

        assertEquals(
            listOf("b" to 0, "c" to 1, "a" to 1, "y" to 2).sortedBy { it.first },
            changed.map { it.id to it.position }.sortedBy { it.first },
        )
        assertEquals("col-2", changed.first { it.id == "a" }.columnId)
        assertEquals("col-1", changed.first { it.id == "b" }.columnId)
    }

    @Test
    fun movedTask_marcaPendingYUpdatedAtSoloEnLasCambiadas() {
        val a = tk("a", "col-1", 0); val b = tk("b", "col-1", 1)
        val x = tk("x", "col-2", 0)

        val changed = ok(movedTask(b, listOf(a, b), listOf(x), "col-2", 1, later))

        assertEquals(listOf("b"), changed.map { it.id })
        assertEquals(later, changed.single().sync.updatedAt)
        assertTrue(changed.single().sync.pending)
    }

    @Test
    fun movedTask_dentroDeLaColumna_desplazaLasDemas() {
        val a = tk("a", "col-1", 0); val b = tk("b", "col-1", 1); val c = tk("c", "col-1", 2)

        val changed = ok(movedTask(c, listOf(a, b, c), listOf(a, b, c), "col-1", 0, later))

        assertEquals(
            mapOf("c" to 0, "a" to 1, "b" to 2),
            changed.associate { it.id to it.position },
        )
    }

    @Test
    fun movedTask_mismoIndice_noCambiaNada() {
        val a = tk("a", "col-1", 0); val b = tk("b", "col-1", 1)

        assertTrue(ok(movedTask(b, listOf(a, b), listOf(a, b), "col-1", 1, later)).isEmpty())
    }

    @Test
    fun movedTask_alFinalDeColumnaVacia_usaIndiceCero() {
        val a = tk("a", "col-1", 0)

        val changed = ok(movedTask(a, listOf(a), emptyList(), "col-2", 0, later))

        assertEquals(listOf("a" to 0), changed.map { it.id to it.position })
        assertEquals("col-2", changed.single().columnId)
    }

    @Test
    fun movedTask_alFinalDeOtraColumna_aceptaIndiceIgualAlTamano() {
        val a = tk("a", "col-1", 0)
        val x = tk("x", "col-2", 0)

        val changed = ok(movedTask(a, listOf(a), listOf(x), "col-2", 1, later))

        assertEquals(1, changed.single().position)
    }

    @Test
    fun movedTask_indiceFueraDeRango_fallaConValidation() {
        val a = tk("a", "col-1", 0); val b = tk("b", "col-1", 1)
        val x = tk("x", "col-2", 0)

        val sameColumn = movedTask(a, listOf(a, b), listOf(a, b), "col-1", 2, later) as DomainResult.Failure
        val otherColumn = movedTask(a, listOf(a, b), listOf(x), "col-2", 2, later) as DomainResult.Failure
        val negative = movedTask(a, listOf(a, b), listOf(x), "col-2", -1, later) as DomainResult.Failure

        listOf(sameColumn, otherColumn, negative).forEach {
            assertTrue(it.error is DomainError.Validation)
            assertEquals("TASK_INDEX_OUT_OF_BOUNDS", it.error.code)
        }
    }

    @Test
    fun movedTask_conHuecosYRepetidos_losNormalizaPorPositionEId() {
        val a = tk("a", "col-1", 0); val b = tk("b", "col-1", 7); val c = tk("c", "col-1", 7)
        val x = tk("x", "col-2", 4)

        val changed = ok(movedTask(a, listOf(c, b, a), listOf(x), "col-2", 0, later))

        assertEquals(
            mapOf("b" to 0, "c" to 1, "a" to 0, "x" to 1),
            changed.associate { it.id to it.position },
        )
    }

    @Test
    fun checkSameBoard_mismoTablero_devuelveNull() {
        assertNull(checkSameBoard("tablero-1", columnOf("tablero-1")))
    }

    @Test
    fun checkSameBoard_otroTablero_devuelveValidationColumnBoardMismatch() {
        val error = checkSameBoard("tablero-1", columnOf("tablero-2"))

        assertTrue(error is DomainError.Validation)
        assertEquals("COLUMN_BOARD_MISMATCH", error?.code)
        assertEquals("La columna destino pertenece a otro tablero", error?.message)
    }

    private fun tk(id: String, columnId: String, position: Int): Task =
        task(columnId = columnId, position = position, sync = SyncMetadata(id, now, pending = false, deletedAt = null))

    private fun ok(result: DomainResult<List<Task>>): List<Task> = (result as DomainResult.Success).value

    private fun columnOf(boardId: String) = Column(
        boardId = boardId,
        name = "Columna",
        position = 0,
        wipLimit = null,
        isDoneColumn = false,
        createdAt = Instant.EPOCH,
        sync = SyncMetadata("col-x", now, pending = false, deletedAt = null),
    )

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
        createdAt = Instant.EPOCH,
        sync = sync,
    )
}
