package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.BoardDao
import com.simposio.taskeer.data.local.BoardEntity
import com.simposio.taskeer.data.local.ColumnDao
import com.simposio.taskeer.data.local.ColumnEntity
import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColumnRepositoryImplTest {

    private val now = Instant.parse("2026-10-15T10:00:00Z")
    private val later = now.plusSeconds(60)

    @Test
    fun observeAllColumns_mapeaYConservaOrden() = runTest {
        val columnDao = FakeColumnDao()
        columnDao.put(columnEntity("c1", name = "Segunda", position = 1))
        columnDao.put(columnEntity("c0", name = "Primera", position = 0))
        val repository = ColumnRepositoryImpl(FakeBoardDao(), columnDao, now = { now })

        val columns = repository.observeAllColumns().first()

        assertEquals(listOf("Primera", "Segunda"), columns.map { it.name })
    }

    @Test
    fun createColumn_anhadeLaColumnaAlFinalYPersiste() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        columnDao.put(columnEntity("c0", name = "Por hacer", position = 0))
        columnDao.put(columnEntity("c1", name = "En progreso", position = 1))
        columnDao.put(columnEntity("c2", name = "Hecho", position = 2, isDoneColumn = true))
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { now })

        val result = repository.createColumn(BOARD_ID, "  Nuevas  ")

        val created = (result as DomainResult.Success).value
        assertEquals("Nuevas", created.name)
        assertEquals(3, created.position)
        assertEquals(BOARD_ID, created.boardId)
        assertNull(created.wipLimit)
        assertFalse(created.isDoneColumn)
        assertEquals(now, created.sync.updatedAt)
        assertTrue(created.sync.pending)
        assertNull(created.sync.deletedAt)

        val row = columnDao.stored.getValue(created.id)
        assertEquals("Nuevas", row.name)
        assertEquals(3, row.position)
        assertTrue(row.pending)
        assertNull(row.deletedAt)

        assertEquals(
            listOf("Por hacer", "En progreso", "Hecho", "Nuevas"),
            repository.observeColumns(BOARD_ID).first().map { it.name },
        )
    }

    @Test
    fun createColumn_sinNombre_noEscribeEnElDao() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { now })

        val result = repository.createColumn(BOARD_ID, "   ")

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.Validation)
        assertEquals("COLUMN_NAME_REQUIRED", error.code)
        assertEquals(0, columnDao.writeCalls)
        assertTrue(columnDao.stored.isEmpty())
    }

    @Test
    fun createColumn_deUnTableroDesconocido_fallaConNotFound() = runTest {
        val boardDao = FakeBoardDao()
        val columnDao = FakeColumnDao()
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { now })

        val unknown = repository.createColumn("tablero-x", "Nueva")

        val unknownError = (unknown as DomainResult.Failure).error
        assertTrue(unknownError is DomainError.NotFound)
        assertEquals("BOARD_NOT_FOUND", unknownError.code)

        boardDao.put(boardEntity(deleted = true))
        val deleted = repository.createColumn(BOARD_ID, "Nueva")

        val deletedError = (deleted as DomainResult.Failure).error
        assertTrue(deletedError is DomainError.NotFound)
        assertEquals("BOARD_NOT_FOUND", deletedError.code)
        assertEquals(0, columnDao.writeCalls)
    }

    @Test
    fun renameColumn_persisteElNombreYConservaElResto() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        columnDao.put(
            columnEntity("c1", name = "Vieja", position = 1, wipLimit = 5, isDoneColumn = true, updatedAt = now),
        )
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { later })

        val result = repository.renameColumn("c1", "  Nueva  ")

        val renamed = (result as DomainResult.Success).value
        assertEquals("Nueva", renamed.name)
        assertEquals("c1", renamed.id)
        assertEquals(BOARD_ID, renamed.boardId)
        assertEquals(1, renamed.position)
        assertEquals(5, renamed.wipLimit)
        assertTrue(renamed.isDoneColumn)
        assertNull(renamed.sync.deletedAt)
        assertEquals(later, renamed.sync.updatedAt)
        assertTrue(renamed.sync.pending)

        val row = columnDao.stored.getValue("c1")
        assertEquals("Nueva", row.name)
        assertEquals(1, row.position)
        assertEquals(5, row.wipLimit)
        assertTrue(row.isDoneColumn)
        assertEquals(later.toEpochMilli(), row.updatedAt)

        val second = ColumnRepositoryImpl(boardDao, columnDao, now = { later })
        assertEquals(listOf("Nueva"), second.observeColumns(BOARD_ID).first().map { it.name })
    }

    @Test
    fun renameColumn_sinNombre_noEscribeEnElDao() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        columnDao.put(columnEntity("c1", name = "Vieja", position = 0))
        val before = columnDao.stored.getValue("c1")
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { later })

        val result = repository.renameColumn("c1", "   ")

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.Validation)
        assertEquals("COLUMN_NAME_REQUIRED", error.code)
        assertEquals(before, columnDao.stored.getValue("c1"))
    }

    @Test
    fun renameColumn_deUnaColumnaDesconocida_fallaConNotFound() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { later })

        val result = repository.renameColumn("no-existe", "Nueva")

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.NotFound)
        assertEquals("COLUMN_NOT_FOUND", error.code)
        assertEquals(0, columnDao.writeCalls)
    }

    @Test
    fun moveColumn_persisteLasPosicionesYRelojeaNuevaInstancia() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        columnDao.put(columnEntity("c0", name = "A", position = 0))
        columnDao.put(columnEntity("c1", name = "B", position = 1))
        columnDao.put(columnEntity("c2", name = "C", position = 2))
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { later })

        val result = repository.moveColumn("c2", toIndex = 1)

        val moved = (result as DomainResult.Success).value
        assertEquals("c2", moved.id)
        assertEquals(1, moved.position)
        assertEquals(later, moved.sync.updatedAt)
        assertTrue(moved.sync.pending)

        val ordered = columnDao.stored.values.sortedBy { it.position }
        assertEquals(listOf("c0", "c2", "c1"), ordered.map { it.id })
        assertEquals(listOf(0, 1, 2), ordered.map { it.position })
        assertEquals("solo se persisten las filas cuya posición cambia", setOf("c1", "c2"), columnDao.lastBulkIds.toSet())

        val second = ColumnRepositoryImpl(boardDao, columnDao, now = { now })
        assertEquals(listOf("c0", "c2", "c1"), second.observeColumns(BOARD_ID).first().map { it.id })
    }

    @Test
    fun moveColumn_alIndiceOcupadoNoEscribe() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        columnDao.put(columnEntity("c0", name = "A", position = 0))
        columnDao.put(columnEntity("c1", name = "B", position = 1, updatedAt = now))
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { later })
        val writesBefore = columnDao.writeCalls

        val result = repository.moveColumn("c1", toIndex = 1)

        assertTrue(result is DomainResult.Success)
        assertEquals(writesBefore, columnDao.writeCalls)
        assertEquals(now.toEpochMilli(), columnDao.stored.getValue("c1").updatedAt)
    }

    @Test
    fun moveColumn_indiceFueraDeRango_noEscribe() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        columnDao.put(columnEntity("c0", name = "A", position = 0))
        columnDao.put(columnEntity("c1", name = "B", position = 1))
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { later })
        val writesBefore = columnDao.writeCalls

        val result = repository.moveColumn("c0", toIndex = 5)

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.Validation)
        assertEquals("COLUMN_INDEX_OUT_OF_BOUNDS", error.code)
        assertEquals(writesBefore, columnDao.writeCalls)
    }

    @Test
    fun moveColumn_deUnaColumnaDesconocida_fallaConNotFound() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { later })

        val result = repository.moveColumn("no-existe", toIndex = 0)

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.NotFound)
        assertEquals("COLUMN_NOT_FOUND", error.code)
        assertEquals(0, columnDao.writeCalls)
    }

    @Test
    fun deleteColumn_marcaDeletedAtYNoBorraLaFila() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        columnDao.put(columnEntity("c0", name = "Por hacer", position = 0, updatedAt = now))
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { later })

        val result = repository.deleteColumn("c0")

        assertTrue(result is DomainResult.Success)
        val row = columnDao.stored.getValue("c0")
        assertEquals(later.toEpochMilli(), row.deletedAt)
        assertEquals(later.toEpochMilli(), row.updatedAt)
        assertTrue(row.pending)
        assertEquals("Por hacer", row.name)
        assertEquals("la fila no se borra físicamente", 1, columnDao.stored.size)
    }

    @Test
    fun deleteColumn_renumeraLasRestantesDensamente() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        columnDao.put(columnEntity("c0", name = "A", position = 0))
        columnDao.put(columnEntity("c1", name = "B", position = 1))
        columnDao.put(columnEntity("c2", name = "C", position = 2))
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { later })

        repository.deleteColumn("c1")

        assertEquals(listOf("c0", "c2"), repository.observeColumns(BOARD_ID).first().map { it.id })
        assertEquals(listOf(0, 1), columnDao.stored.values.filter { it.deletedAt == null }.sortedBy { it.position }.map { it.position })
        assertTrue(columnDao.stored.containsKey("c1"))
    }

    @Test
    fun deleteColumn_conTareas_devuelveConflictYSinEscribir() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        columnDao.put(columnEntity("c0", name = "A", position = 0))
        val before = columnDao.stored
        val repository = ColumnRepositoryImpl(
            boardDao,
            columnDao,
            columnTaskCount = { 2 },
            now = { later },
        )

        val result = repository.deleteColumn("c0")

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.Conflict)
        assertEquals("COLUMN_NOT_EMPTY", error.code)
        assertEquals(before, columnDao.stored)
        assertEquals(0, columnDao.writeCalls)
    }

    @Test
    fun deleteColumn_deUnaColumnaBorrada_fallaConNotFound() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        columnDao.put(columnEntity("c0", name = "A", position = 0))
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { later })
        repository.deleteColumn("c0")
        val afterDelete = columnDao.stored.getValue("c0")
        val writesAfterDelete = columnDao.writeCalls

        val result = repository.deleteColumn("c0")

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.NotFound)
        assertEquals("COLUMN_NOT_FOUND", error.code)
        assertEquals(afterDelete, columnDao.stored.getValue("c0"))
        assertEquals(writesAfterDelete, columnDao.writeCalls)
    }

    @Test
    fun observeColumns_emiteSoloLasActivasEnOrdenDePosition() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        columnDao.put(columnEntity("c1", name = "B", position = 1))
        columnDao.put(columnEntity("c0", name = "A", position = 0))
        columnDao.put(columnEntity("c2", name = "C", position = 2, deletedAt = later))
        columnDao.put(columnEntity("otro", name = "Ajena", position = 0, boardId = "tablero-x"))
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { now })

        val columns = repository.observeColumns(BOARD_ID).first()

        assertEquals(listOf("c0", "c1"), columns.map { it.id })
        assertEquals(listOf(0, 1), columns.map { it.position })
    }

    @Test
    fun observeColumns_desempataPorIdConPosicionesIguales() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        columnDao.put(columnEntity("b", name = "B", position = 0))
        columnDao.put(columnEntity("a", name = "A", position = 0))
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { now })

        val columns = repository.observeColumns(BOARD_ID).first()

        assertEquals(listOf("a", "b"), columns.map { it.id })
    }

    @Test
    fun renameColumn_nombreDe101_noEscribeYNombraElLimite() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        columnDao.put(columnEntity("c1", name = "Vieja", position = 0))
        val writesBefore = columnDao.writeCalls
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { later })

        val result = repository.renameColumn("c1", "x".repeat(101))

        val error = (result as DomainResult.Failure).error
        assertEquals("COLUMN_NAME_TOO_LONG", error.code)
        assertTrue(error.message.contains("100"))
        assertEquals(writesBefore, columnDao.writeCalls)
    }

    @Test
    fun createColumn_nombreDe101_noEscribe() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { now })

        val result = repository.createColumn(BOARD_ID, "x".repeat(101))

        assertEquals("COLUMN_NAME_TOO_LONG", (result as DomainResult.Failure).error.code)
        assertEquals(0, columnDao.writeCalls)
    }

    @Test
    fun createColumn_fijaCreatedAtConElNow() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { now })

        val created = (repository.createColumn(BOARD_ID, "Nueva") as DomainResult.Success).value

        assertEquals(now, created.createdAt)
        assertEquals(now.toEpochMilli(), columnDao.stored.getValue(created.id).createdAt)
    }

    @Test
    fun setWipLimit_fijaElLimiteYMarcaPendienteConUnaEscritura() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        val original = columnEntity("c1", name = "Col", position = 2, isDoneColumn = true, pending = false)
            .copy(createdAt = 42L)
        columnDao.put(original)
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { later })

        val result = repository.setWipLimit("c1", 3)

        assertEquals(3, (result as DomainResult.Success).value.wipLimit)
        assertEquals(
            original.copy(wipLimit = 3, pending = true, updatedAt = later.toEpochMilli()),
            columnDao.stored.getValue("c1"),
        )
        assertEquals(1, columnDao.writeCalls)
    }

    @Test
    fun setWipLimit_conNulo_quitaElLimite() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        columnDao.put(columnEntity("c1", name = "Col", position = 0, wipLimit = 5))
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { later })

        val result = repository.setWipLimit("c1", null)

        assertNull((result as DomainResult.Success).value.wipLimit)
        assertNull(columnDao.stored.getValue("c1").wipLimit)
    }

    @Test
    fun setWipLimit_menorQueUno_devuelveElErrorSinEscribir() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        columnDao.put(columnEntity("c1", name = "Col", position = 0))
        val writesBefore = columnDao.writeCalls
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { later })

        val result = repository.setWipLimit("c1", 0)

        val error = (result as DomainResult.Failure).error
        assertEquals("COLUMN_WIP_LIMIT_INVALID", error.code)
        assertTrue(error.message.contains("1"))
        assertEquals(writesBefore, columnDao.writeCalls)
    }

    @Test
    fun setWipLimit_columnaInexistenteOBorrada_devuelveNotFoundSinEscribir() = runTest {
        val boardDao = FakeBoardDao().apply { put(boardEntity()) }
        val columnDao = FakeColumnDao()
        columnDao.put(columnEntity("borrada", name = "Col", position = 0, deletedAt = now))
        val writesBefore = columnDao.writeCalls
        val repository = ColumnRepositoryImpl(boardDao, columnDao, now = { later })

        listOf("borrada", "no-existe").forEach { id ->
            val error = (repository.setWipLimit(id, 2) as DomainResult.Failure).error

            assertTrue(error is DomainError.NotFound)
            assertEquals("COLUMN_NOT_FOUND", error.code)
        }
        assertEquals(writesBefore, columnDao.writeCalls)
    }

    private fun boardEntity(id: String = BOARD_ID, deleted: Boolean = false): BoardEntity = BoardEntity(
        id = id,
        name = "Tablero",
        createdAt = now.toEpochMilli(),
        updatedAt = now.toEpochMilli(),
        pending = false,
        deletedAt = if (deleted) now.toEpochMilli() else null,
    )

    private fun columnEntity(
        id: String,
        name: String,
        position: Int,
        boardId: String = BOARD_ID,
        wipLimit: Int? = null,
        isDoneColumn: Boolean = false,
        updatedAt: Instant = now,
        pending: Boolean = true,
        deletedAt: Instant? = null,
    ): ColumnEntity = ColumnEntity(
        id = id,
        boardId = boardId,
        name = name,
        position = position,
        wipLimit = wipLimit,
        isDoneColumn = isDoneColumn,
        createdAt = 0L,
        updatedAt = updatedAt.toEpochMilli(),
        pending = pending,
        deletedAt = deletedAt?.toEpochMilli(),
    )

    private class FakeBoardDao : BoardDao {
        private val rows = MutableStateFlow<Map<String, BoardEntity>>(emptyMap())

        val stored: Map<String, BoardEntity> get() = rows.value

        fun put(board: BoardEntity) {
            rows.value = rows.value + (board.id to board)
        }

        override fun observeActive(): Flow<List<BoardEntity>> = rows.map { storedRows ->
            storedRows.values
                .filter { it.deletedAt == null }
                .sortedWith(compareBy({ it.createdAt }, { it.id }))
        }

        override suspend fun findById(id: String): BoardEntity? = rows.value[id]

        override suspend fun upsert(board: BoardEntity) {
            rows.value = rows.value + (board.id to board)
        }
    }

    private class FakeColumnDao : ColumnDao() {
        private val rows = MutableStateFlow<Map<String, ColumnEntity>>(emptyMap())

        override fun observeAllActive(): Flow<List<ColumnEntity>> = rows.map { storedRows ->
            storedRows.values
                .filter { it.deletedAt == null }
                .sortedWith(compareBy({ it.boardId }, { it.position }, { it.id }))
        }

        var writeCalls: Int = 0
            private set

        var lastBulkIds: List<String> = emptyList()
            private set

        val stored: Map<String, ColumnEntity> get() = rows.value

        fun put(column: ColumnEntity) {
            rows.value = rows.value + (column.id to column)
        }

        override fun observeByBoard(boardId: String): Flow<List<ColumnEntity>> = rows.map { storedRows ->
            storedRows.values
                .filter { it.boardId == boardId && it.deletedAt == null }
                .sortedWith(compareBy({ it.position }, { it.id }))
        }

        override suspend fun findActiveByBoard(boardId: String): List<ColumnEntity> =
            rows.value.values
                .filter { it.boardId == boardId && it.deletedAt == null }
                .sortedWith(compareBy({ it.position }, { it.id }))

        override suspend fun findById(id: String): ColumnEntity? = rows.value[id]

        override suspend fun upsert(column: ColumnEntity) {
            writeCalls++
            rows.value = rows.value + (column.id to column)
        }

        override suspend fun upsertAll(columns: List<ColumnEntity>) {
            writeCalls++
            lastBulkIds = columns.map { it.id }
            rows.value = rows.value + columns.associateBy { it.id }
        }

        override suspend fun insertBoard(board: BoardEntity) = Unit

        override suspend fun insertBoardWithSeed(board: BoardEntity, columns: List<ColumnEntity>) {
            super.insertBoardWithSeed(board, columns)
        }
    }

    private companion object {
        const val BOARD_ID = "tablero-1"
    }
}
