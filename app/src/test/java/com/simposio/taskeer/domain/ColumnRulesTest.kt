package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.column.COLUMN_INDEX_OUT_OF_BOUNDS
import com.simposio.taskeer.domain.column.COLUMN_NAME_REQUIRED
import com.simposio.taskeer.domain.column.COLUMN_NOT_EMPTY
import com.simposio.taskeer.domain.column.COLUMN_NOT_FOUND
import com.simposio.taskeer.domain.column.COLUMN_WIP_LIMIT_REACHED
import com.simposio.taskeer.domain.column.checkWipLimit
import com.simposio.taskeer.domain.column.defaultBoardColumns
import com.simposio.taskeer.domain.column.deletedIfEmpty
import com.simposio.taskeer.domain.column.movedTo
import com.simposio.taskeer.domain.column.newColumn
import com.simposio.taskeer.domain.column.renamed
import com.simposio.taskeer.domain.column.renumbered
import com.simposio.taskeer.domain.column.softDeleted
import com.simposio.taskeer.domain.column.withWipLimit
import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.SyncMetadata
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ColumnRulesTest {

    private val now = Instant.parse("2026-10-15T10:00:00Z")
    private val later = now.plusSeconds(60)

    @Test
    fun column_exponeIdYEstadoDesdeLosMetadatos() {
        val active = column(id = "uuid-1", name = "Por hacer", position = 0)

        assertEquals("uuid-1", active.id)
        assertFalse(active.isDeleted)

        val deleted = column(id = "uuid-2", name = "Borrada", position = 0, deletedAt = later)

        assertTrue(deleted.isDeleted)
    }

    @Test
    fun defaultBoardColumns_siembraTresColumnasConHechoCompletadas() {
        val ids = ArrayDeque(listOf("id-0", "id-1", "id-2"))

        val columns = defaultBoardColumns("tablero-1", now) { ids.removeFirst() }

        assertEquals(listOf("Por hacer", "En progreso", "Hecho"), columns.map { it.name })
        assertEquals(listOf(0, 1, 2), columns.map { it.position })
        assertEquals(listOf(false, false, true), columns.map { it.isDoneColumn })
        assertTrue("todas nacen sin límite WIP", columns.all { it.wipLimit == null })
        assertTrue("todas nacen pendientes", columns.all { it.sync.pending })
        assertTrue("ninguna nace borrada", columns.all { it.sync.deletedAt == null })
        assertTrue("todas con el instante de creación", columns.all { it.sync.updatedAt == now })
        assertTrue("todas apuntan al tablero", columns.all { it.boardId == "tablero-1" })
        assertEquals(listOf("id-0", "id-1", "id-2"), columns.map { it.id })
    }

    @Test
    fun newColumn_recortaElNombreYNacePendiente() {
        val result = newColumn("tablero-1", "  Nuevas  ", position = 3, now = now, clientId = "uuid-9")

        val created = (result as DomainResult.Success).value

        assertEquals("Nuevas", created.name)
        assertEquals(3, created.position)
        assertEquals("tablero-1", created.boardId)
        assertNull(created.wipLimit)
        assertFalse(created.isDoneColumn)
        assertEquals(now, created.sync.updatedAt)
        assertTrue(created.sync.pending)
        assertNull(created.sync.deletedAt)
        assertEquals("uuid-9", created.id)
    }

    @Test
    fun newColumn_sinNombre_fallaConValidation() {
        val result = newColumn("tablero-1", "   ", position = 0, now = now)

        val error = (result as DomainResult.Failure).error

        assertTrue(error is DomainError.Validation)
        assertEquals(COLUMN_NAME_REQUIRED, error.code)
    }

    @Test
    fun renamed_cambiaElNombreYNoTocaElResto() {
        val original = column(
            id = "uuid-1",
            name = "Vieja",
            position = 2,
            wipLimit = 5,
            isDoneColumn = true,
            updatedAt = now,
            pending = false,
        )

        val result = original.renamed("  Nueva  ", later)

        val renamed = (result as DomainResult.Success).value

        assertEquals("Nueva", renamed.name)
        assertEquals(original.id, renamed.id)
        assertEquals(original.boardId, renamed.boardId)
        assertEquals(original.position, renamed.position)
        assertEquals(original.wipLimit, renamed.wipLimit)
        assertEquals(original.isDoneColumn, renamed.isDoneColumn)
        assertNull(renamed.sync.deletedAt)
        assertEquals(later, renamed.sync.updatedAt)
        assertTrue(renamed.sync.pending)
    }

    @Test
    fun renamed_sinNombre_fallaConValidation() {
        val original = column(id = "uuid-1", name = "Vieja", position = 0)

        val result = original.renamed("  ", later)

        val error = (result as DomainResult.Failure).error

        assertTrue(error is DomainError.Validation)
        assertEquals(COLUMN_NAME_REQUIRED, error.code)
    }

    @Test
    fun softDeleted_marcaDeletedAtSinCambiarElResto() {
        val original = column(
            id = "uuid-1",
            name = "Columna",
            position = 1,
            wipLimit = 3,
            isDoneColumn = true,
            updatedAt = now,
            pending = false,
        )

        val deleted = original.softDeleted(later)

        assertEquals(later, deleted.sync.deletedAt)
        assertEquals(later, deleted.sync.updatedAt)
        assertTrue(deleted.sync.pending)
        assertEquals(original.id, deleted.id)
        assertEquals(original.name, deleted.name)
        assertEquals(original.boardId, deleted.boardId)
        assertEquals(original.position, deleted.position)
        assertEquals(original.wipLimit, deleted.wipLimit)
        assertEquals(original.isDoneColumn, deleted.isDoneColumn)
    }

    @Test
    fun deletedIfEmpty_conTareas_devuelveConflict() {
        val original = column(id = "uuid-1", name = "Columna", position = 0)

        val result = original.deletedIfEmpty(taskCount = 2, now = later)

        val error = (result as DomainResult.Failure).error

        assertTrue(error is DomainError.Conflict)
        assertEquals(COLUMN_NOT_EMPTY, error.code)
        assertNull("no debe marcar nada al rechazar", original.sync.deletedAt)
    }

    @Test
    fun deletedIfEmpty_sinTareas_marcaDeletedAt() {
        val original = column(id = "uuid-1", name = "Columna", position = 0)

        val result = original.deletedIfEmpty(taskCount = 0, now = later)

        val deleted = (result as DomainResult.Success).value

        assertEquals(later, deleted.sync.deletedAt)
        assertTrue(deleted.sync.pending)
    }

    @Test
    fun renumbered_renumeraDensamenteYSoloMarcaLasQueCambian() {
        val first = column(id = "a", name = "A", position = 0, updatedAt = now)
        val second = column(id = "b", name = "B", position = 2, updatedAt = now)
        val third = column(id = "c", name = "C", position = 3, updatedAt = now)

        val result = listOf(first, second, third).renumbered(later)

        assertEquals(listOf(0, 1, 2), result.map { it.position })
        assertEquals(listOf("a", "b", "c"), result.map { it.id })
        assertSame("la que no cambia se devuelve intacta", first, result[0])
        assertEquals(now, result[0].sync.updatedAt)
        assertEquals(later, result[1].sync.updatedAt)
        assertTrue(result[1].sync.pending)
        assertEquals(later, result[2].sync.updatedAt)
        assertTrue(result[2].sync.pending)
    }

    @Test
    fun movedTo_renumeraDensamenteSoloLasFilasCambiadas() {
        val first = column(id = "a", name = "A", position = 0, updatedAt = now)
        val second = column(id = "b", name = "B", position = 1, updatedAt = now)
        val third = column(id = "c", name = "C", position = 2, updatedAt = now)

        val result = listOf(first, second, third).movedTo("c", toIndex = 1, now = later)

        val moved = (result as DomainResult.Success).value

        assertEquals(listOf("a", "c", "b"), moved.map { it.id })
        assertEquals(listOf(0, 1, 2), moved.map { it.position })
        assertEquals(now, moved[0].sync.updatedAt)
        assertEquals(later, moved[1].sync.updatedAt)
        assertTrue(moved[1].sync.pending)
        assertEquals(later, moved[2].sync.updatedAt)
        assertTrue(moved[2].sync.pending)
    }

    @Test
    fun movedTo_alPropioIndiceNoCambiaNada() {
        val list = listOf(
            column(id = "a", name = "A", position = 0, updatedAt = now),
            column(id = "b", name = "B", position = 1, updatedAt = now),
            column(id = "c", name = "C", position = 2, updatedAt = now),
        )

        val result = list.movedTo("b", toIndex = 1, now = later)

        val moved = (result as DomainResult.Success).value

        assertEquals(list, moved)
        assertTrue("ninguna fila cambia", moved.all { it.sync.updatedAt == now })
    }

    @Test
    fun movedTo_indiceFueraDeRango_fallaConValidation() {
        val list = listOf(
            column(id = "a", name = "A", position = 0),
            column(id = "b", name = "B", position = 1),
        )

        val result = list.movedTo("a", toIndex = 2, now = later)

        val error = (result as DomainResult.Failure).error

        assertTrue(error is DomainError.Validation)
        assertEquals(COLUMN_INDEX_OUT_OF_BOUNDS, error.code)
    }

    @Test
    fun movedTo_columnaDesconocida_fallaConNotFound() {
        val list = listOf(column(id = "a", name = "A", position = 0))

        val result = list.movedTo("no-existe", toIndex = 0, now = later)

        val error = (result as DomainResult.Failure).error

        assertTrue(error is DomainError.NotFound)
        assertEquals(COLUMN_NOT_FOUND, error.code)
    }

    @Test
    fun newColumn_longitudesDelNombre() {
        fun code(length: Int): String? =
            (newColumn("tablero-1", "x".repeat(length), 0, now) as? DomainResult.Failure)?.error?.code

        assertEquals("COLUMN_NAME_REQUIRED", code(0))
        assertNull(code(1))
        assertNull(code(100))
        assertEquals("COLUMN_NAME_TOO_LONG", code(101))
    }

    @Test
    fun renamed_nombreDe101_fallaConMensajeQueNombraElLimite() {
        val error = (column("c1", "A", 0).renamed("x".repeat(101), later) as DomainResult.Failure).error

        assertTrue(error is DomainError.Validation)
        assertEquals("COLUMN_NAME_TOO_LONG", error.code)
        assertEquals("El nombre de la columna no puede superar los 100 caracteres", error.message)
    }

    @Test
    fun withWipLimit_nuloYMayorOIgualAUno_sonValidos() {
        val original = column("c1", "A", 0, wipLimit = 4)

        val cleared = (original.withWipLimit(null, later) as DomainResult.Success).value
        val one = (original.withWipLimit(1, later) as DomainResult.Success).value

        assertNull(cleared.wipLimit)
        assertEquals(1, one.wipLimit)
        assertTrue(one.sync.pending)
        assertEquals(later, one.sync.updatedAt)
    }

    @Test
    fun withWipLimit_menorQueUno_fallaConValidation() {
        listOf(0, -1).forEach { limit ->
            val error = (column("c1", "A", 0).withWipLimit(limit, later) as DomainResult.Failure).error

            assertTrue(error is DomainError.Validation)
            assertEquals("COLUMN_WIP_LIMIT_INVALID", error.code)
            assertEquals("El valor mínimo del límite WIP es 1", error.message)
        }
    }

    @Test
    fun createdAt_alCrear_esElNow() {
        val created = (newColumn("tablero-1", "Nueva", 0, now) as DomainResult.Success).value
        val seeded = defaultBoardColumns("tablero-1", now)

        assertEquals(now, created.createdAt)
        assertTrue(seeded.all { it.createdAt == now })
    }

    @Test
    fun createdAt_sobreviveACualquierEdicionConOtroNow() {
        val born = column("c1", "A", 0).copy(createdAt = now.minusSeconds(500))
        val other = born.copy(sync = born.sync.copy(clientId = "c2"), position = 1)
        val otherNow = later.plusSeconds(999)

        val edited = listOf(
            (born.renamed("B", otherNow) as DomainResult.Success).value,
            (born.withWipLimit(3, otherNow) as DomainResult.Success).value,
            born.softDeleted(otherNow),
            listOf(born, other).renumbered(otherNow).first(),
            (listOf(other, born).movedTo("c1", 0, otherNow) as DomainResult.Success).value.first { it.id == "c1" },
        )

        edited.forEach { assertEquals(born.createdAt, it.createdAt) }
    }

    @Test
    fun checkWipLimit_sinLimite_noBloquea() {
        assertNull(checkWipLimit(column("c1", "A", 0, wipLimit = null), 50))
    }

    @Test
    fun checkWipLimit_conHueco_noBloquea() {
        assertNull(checkWipLimit(column("c1", "A", 0, wipLimit = 3), 2))
    }

    @Test
    fun checkWipLimit_alcanzado_devuelveConflictConElCodigo() {
        val error = checkWipLimit(column("c1", "A", 0, wipLimit = 3), 3)

        assertTrue(error is DomainError.Conflict)
        assertEquals(COLUMN_WIP_LIMIT_REACHED, error?.code)
    }

    @Test
    fun checkWipLimit_sobrepasado_devuelveConflict() {
        assertEquals(COLUMN_WIP_LIMIT_REACHED, checkWipLimit(column("c1", "A", 0, wipLimit = 3), 5)?.code)
    }

    @Test
    fun checkWipLimit_mensajeIncluyeNombreYLimite() {
        val message = checkWipLimit(column("c1", "Revisión", 0, wipLimit = 3), 3)?.message.orEmpty()

        assertTrue(message.contains("Revisión"))
        assertTrue(message.contains("3"))
    }

    private fun column(
        id: String,
        name: String,
        position: Int,
        boardId: String = "tablero-1",
        wipLimit: Int? = null,
        isDoneColumn: Boolean = false,
        updatedAt: Instant = now,
        pending: Boolean = false,
        deletedAt: Instant? = null,
    ): Column = Column(
        boardId = boardId,
        name = name,
        position = position,
        wipLimit = wipLimit,
        isDoneColumn = isDoneColumn,
        createdAt = Instant.EPOCH,
        sync = SyncMetadata(
            clientId = id,
            updatedAt = updatedAt,
            pending = pending,
            deletedAt = deletedAt,
        ),
    )
}
