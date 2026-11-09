package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.task.buildBoardDestinations
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardDestinationsTest {

    private val t0: Instant = Instant.parse("2026-10-01T10:00:00Z")

    @Test
    fun ordenaTablerosPorCreacionYColumnasPorPosicion() {
        val boards = listOf(board("b2", createdAt = t0.plusSeconds(10)), board("b1", createdAt = t0))
        val columns = listOf(
            column("c-b", "b1", position = 1),
            column("c-a", "b1", position = 0),
            column("c-x", "b2", position = 0),
        )

        val result = buildBoardDestinations(boards, columns, emptyMap(), includeDoneColumns = true)

        assertEquals(listOf("b1", "b2"), result.map { it.board.id })
        assertEquals(listOf("c-a", "c-b"), result.first().columns.map { it.column.id })
    }

    @Test
    fun excluyeColumnasDeCierreSiSeIndica() {
        val columns = listOf(column("c1", "b1", 0), column("hecho", "b1", 1, isDone = true))

        val result = buildBoardDestinations(listOf(board("b1")), columns, emptyMap(), includeDoneColumns = false)

        assertEquals(listOf("c1"), result.single().columns.map { it.column.id })
    }

    @Test
    fun incluyeColumnasDeCierreSiSeIndica() {
        val columns = listOf(column("c1", "b1", 0), column("hecho", "b1", 1, isDone = true))

        val result = buildBoardDestinations(listOf(board("b1")), columns, emptyMap(), includeDoneColumns = true)

        assertEquals(listOf("c1", "hecho"), result.single().columns.map { it.column.id })
    }

    @Test
    fun ignoraTablerosYColumnasBorrados() {
        val boards = listOf(board("b1"), board("b-borrado", deleted = true))
        val columns = listOf(column("c1", "b1", 0), column("c-borrada", "b1", 1, deleted = true), column("c-x", "b-borrado", 0))

        val result = buildBoardDestinations(boards, columns, emptyMap(), includeDoneColumns = true)

        assertEquals(listOf("b1"), result.map { it.board.id })
        assertEquals(listOf("c1"), result.single().columns.map { it.column.id })
    }

    @Test
    fun marcaColumnaLlenaPorWip() {
        val columns = listOf(column("llena", "b1", 0, wipLimit = 2), column("hueco", "b1", 1, wipLimit = 3))

        val result = buildBoardDestinations(listOf(board("b1")), columns, mapOf("llena" to 2, "hueco" to 2), true)

        assertEquals(listOf(true, false), result.single().columns.map { it.isFull })
    }

    @Test
    fun conservaTableroSinColumnas() {
        val columns = listOf(column("hecho", "b1", 0, isDone = true))

        val result = buildBoardDestinations(listOf(board("b1")), columns, emptyMap(), includeDoneColumns = false)

        assertEquals(listOf("b1"), result.map { it.board.id })
        assertTrue(result.single().columns.isEmpty())
    }

    @Test
    fun incluyeOcupacionPorColumna() {
        val columns = listOf(column("c1", "b1", 0), column("c2", "b1", 1))

        val result = buildBoardDestinations(listOf(board("b1")), columns, mapOf("c1" to 3), true)

        assertEquals(listOf(3, 0), result.single().columns.map { it.activeCount })
        assertFalse(result.single().columns.any { it.isFull })
    }

    private fun board(id: String, createdAt: Instant = t0, deleted: Boolean = false) = Board(
        name = "Tablero $id",
        createdAt = createdAt,
        sync = SyncMetadata(id, t0, pending = false, deletedAt = if (deleted) t0 else null),
    )

    private fun column(
        id: String,
        boardId: String,
        position: Int,
        isDone: Boolean = false,
        wipLimit: Int? = null,
        deleted: Boolean = false,
    ) = Column(
        boardId = boardId,
        name = "Columna $id",
        position = position,
        wipLimit = wipLimit,
        isDoneColumn = isDone,
        createdAt = t0,
        sync = SyncMetadata(id, t0, pending = false, deletedAt = if (deleted) t0 else null),
    )
}
