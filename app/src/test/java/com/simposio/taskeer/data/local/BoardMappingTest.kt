package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.newSyncMetadata
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardMappingTest {

    private val instant = Instant.ofEpochMilli(1_721_000_000_123L)

    @Test
    fun entidadADominio_mapeaNombreYCreacion() {
        val entity = BoardEntity(
            id = "uuid-1",
            name = "Tablero",
            createdAt = instant.toEpochMilli(),
            updatedAt = instant.toEpochMilli(),
            pending = true,
            deletedAt = null,
        )

        val board = entity.toBoard()

        assertEquals("Tablero", board.name)
        assertEquals(instant, board.createdAt)
        assertEquals("uuid-1", board.id)
        assertFalse(board.isDeleted)
    }

    @Test
    fun dominioAEntidad_escribeLosSeisCampos() {
        val updatedAt = instant.plusSeconds(10)
        val deletedAt = instant.plusSeconds(30)
        val board = Board(
            name = "Tablero",
            createdAt = instant,
            sync = SyncMetadata(
                clientId = "uuid-1",
                updatedAt = updatedAt,
                pending = true,
                deletedAt = deletedAt,
            ),
        )

        val entity = board.toEntity()

        assertEquals("uuid-1", entity.id)
        assertEquals("Tablero", entity.name)
        assertEquals(instant.toEpochMilli(), entity.createdAt)
        assertEquals(updatedAt.toEpochMilli(), entity.updatedAt)
        assertTrue(entity.pending)
        assertEquals(deletedAt.toEpochMilli(), entity.deletedAt)
    }

    @Test
    fun idaYVuelta_preservaElInstanteYLosMetadatos() {
        val board = Board(
            name = "Tablero",
            createdAt = instant,
            sync = newSyncMetadata(instant.plusSeconds(5), clientId = "uuid-cliente"),
        )

        val restored = board.toEntity().toBoard()

        assertEquals(board, restored)
    }
}
