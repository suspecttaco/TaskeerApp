package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.BoardDao
import com.simposio.taskeer.data.local.BoardEntity
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardRepositoryImplTest {

    private val now = Instant.parse("2026-10-14T10:00:00Z")

    @Test
    fun createBoard_persisteYPermiteRelistar() = runTest {
        val dao = FakeBoardDao()
        val repository = BoardRepositoryImpl(dao, now = { now })

        val result = repository.createBoard("  Compras  ")

        val board = (result as DomainResult.Success).value
        assertEquals("Compras", board.name)
        assertEquals(now, board.createdAt)
        assertEquals(board.id, UUID.fromString(board.id).toString())

        val row = dao.stored.getValue(board.id)
        assertEquals(board.id, row.id)
        assertEquals("Compras", row.name)
        assertEquals(now.toEpochMilli(), row.createdAt)
        assertEquals(now.toEpochMilli(), row.updatedAt)
        assertTrue(row.pending)
        assertNull(row.deletedAt)

        assertEquals(listOf("Compras"), repository.observeBoards().first().map { it.name })
    }

    @Test
    fun createBoard_sinNombre_noEscribeEnElDao() = runTest {
        val dao = FakeBoardDao()
        val repository = BoardRepositoryImpl(dao, now = { now })

        val result = repository.createBoard("   ")

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.Validation)
        assertEquals("BOARD_NAME_REQUIRED", error.code)
        assertTrue(dao.stored.isEmpty())
    }

    @Test
    fun renameBoard_persisteElNombreYConservaIdYCreacion() = runTest {
        val dao = FakeBoardDao()
        var clock = now
        val repository = BoardRepositoryImpl(dao, now = { clock })
        val created = (repository.createBoard("Compras") as DomainResult.Success).value

        clock = now.plusSeconds(60)
        val renamed = (repository.renameBoard(created.id, "  Mercado  ") as DomainResult.Success).value

        assertEquals(created.id, renamed.id)
        assertEquals(created.createdAt, renamed.createdAt)
        assertEquals("Mercado", renamed.name)
        assertEquals(clock, renamed.sync.updatedAt)
        assertTrue(renamed.sync.pending)

        val row = dao.stored.getValue(created.id)
        assertEquals("Mercado", row.name)
        assertEquals(created.createdAt.toEpochMilli(), row.createdAt)
        assertEquals(clock.toEpochMilli(), row.updatedAt)
    }

    @Test
    fun renameBoard_unaNuevaInstanciaLeeElNombreNuevo() = runTest {
        val dao = FakeBoardDao()
        val first = BoardRepositoryImpl(dao, now = { now })
        val created = (first.createBoard("Compras") as DomainResult.Success).value
        first.renameBoard(created.id, "Mercado")

        val second = BoardRepositoryImpl(dao, now = { now.plusSeconds(90) })

        assertEquals(listOf("Mercado"), second.observeBoards().first().map { it.name })
    }

    @Test
    fun renameBoard_sinNombre_noEscribeEnElDao() = runTest {
        val dao = FakeBoardDao()
        val repository = BoardRepositoryImpl(dao, now = { now })
        val created = (repository.createBoard("Compras") as DomainResult.Success).value
        val before = dao.stored.getValue(created.id)

        val result = repository.renameBoard(created.id, "   ")

        val error = (result as DomainResult.Failure).error
        assertEquals("BOARD_NAME_REQUIRED", error.code)
        assertEquals(before, dao.stored.getValue(created.id))
    }

    @Test
    fun deleteBoard_marcaDeletedAtYNoBorraLaFila() = runTest {
        val dao = FakeBoardDao()
        var clock = now
        val repository = BoardRepositoryImpl(dao, now = { clock })
        val created = (repository.createBoard("Compras") as DomainResult.Success).value

        clock = now.plusSeconds(120)
        val result = repository.deleteBoard(created.id)

        assertTrue(result is DomainResult.Success)
        val row = dao.stored.getValue(created.id)
        assertEquals(clock.toEpochMilli(), row.deletedAt)
        assertEquals(clock.toEpochMilli(), row.updatedAt)
        assertTrue(row.pending)
        assertEquals("Compras", row.name)
        assertEquals(created.createdAt.toEpochMilli(), row.createdAt)
        assertEquals(1, dao.stored.size)
    }

    @Test
    fun observeBoards_excluyeLosBorrados() = runTest {
        val dao = FakeBoardDao()
        var clock = now
        val repository = BoardRepositoryImpl(dao, now = { clock })
        val active = (repository.createBoard("Activo") as DomainResult.Success).value
        val removed = (repository.createBoard("Borrado") as DomainResult.Success).value

        clock = now.plusSeconds(60)
        repository.deleteBoard(removed.id)

        assertEquals(listOf(active.id), repository.observeBoards().first().map { it.id })
        assertTrue(dao.stored.containsKey(removed.id))
    }

    @Test
    fun observeBoards_conservaElOrdenDeCreacion() = runTest {
        val dao = FakeBoardDao()
        var clock = now
        val repository = BoardRepositoryImpl(dao, now = { clock })
        val first = (repository.createBoard("Primero") as DomainResult.Success).value
        clock = now.plusSeconds(1)
        val second = (repository.createBoard("Segundo") as DomainResult.Success).value
        clock = now.plusSeconds(2)
        val third = (repository.createBoard("Tercero") as DomainResult.Success).value

        assertEquals(
            listOf(first.id, second.id, third.id),
            repository.observeBoards().first().map { it.id },
        )
    }

    @Test
    fun renameBoard_deUnIdDesconocido_fallaConNotFound() = runTest {
        val dao = FakeBoardDao()
        val repository = BoardRepositoryImpl(dao, now = { now })

        val result = repository.renameBoard("no-existe", "Nuevo")

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.NotFound)
        assertEquals("BOARD_NOT_FOUND", error.code)
        assertTrue(dao.stored.isEmpty())
    }

    @Test
    fun deleteBoard_deUnIdDesconocido_fallaConNotFound() = runTest {
        val dao = FakeBoardDao()
        val repository = BoardRepositoryImpl(dao, now = { now })

        val result = repository.deleteBoard("no-existe")

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.NotFound)
        assertEquals("BOARD_NOT_FOUND", error.code)
        assertTrue(dao.stored.isEmpty())
    }

    @Test
    fun deleteBoard_deUnTableroYaBorrado_fallaConNotFound() = runTest {
        val dao = FakeBoardDao()
        var clock = now
        val repository = BoardRepositoryImpl(dao, now = { clock })
        val created = (repository.createBoard("Compras") as DomainResult.Success).value
        clock = now.plusSeconds(60)
        repository.deleteBoard(created.id)
        val afterDelete = dao.stored.getValue(created.id)

        clock = now.plusSeconds(120)
        val result = repository.deleteBoard(created.id)

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.NotFound)
        assertEquals("BOARD_NOT_FOUND", error.code)
        assertEquals(afterDelete, dao.stored.getValue(created.id))
    }

    @Test
    fun renameBoard_deUnTableroYaBorrado_fallaConNotFound() = runTest {
        val dao = FakeBoardDao()
        var clock = now
        val repository = BoardRepositoryImpl(dao, now = { clock })
        val created = (repository.createBoard("Compras") as DomainResult.Success).value
        clock = now.plusSeconds(60)
        repository.deleteBoard(created.id)
        val afterDelete = dao.stored.getValue(created.id)

        clock = now.plusSeconds(120)
        val result = repository.renameBoard(created.id, "Mercado")

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.NotFound)
        assertEquals("BOARD_NOT_FOUND", error.code)
        assertEquals(afterDelete, dao.stored.getValue(created.id))
    }

    private class FakeBoardDao : BoardDao {
        private val rows = MutableStateFlow<Map<String, BoardEntity>>(emptyMap())

        val stored: Map<String, BoardEntity> get() = rows.value

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
}
