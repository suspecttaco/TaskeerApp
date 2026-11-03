package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.SyncMetadata
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColumnMappingTest {

    private val instant = Instant.ofEpochMilli(1_721_000_000_123L)

    @Test
    fun entidadADominio_mapeaLosDiezCampos() {
        val entity = ColumnEntity(
            id = "uuid-1",
            boardId = "tablero-1",
            name = "Por hacer",
            position = 2,
            wipLimit = 5,
            isDoneColumn = true,
            createdAt = 0L,
            updatedAt = instant.toEpochMilli(),
            pending = true,
            deletedAt = null,
        )

        val column = entity.toColumn()

        assertEquals("uuid-1", column.id)
        assertEquals("tablero-1", column.boardId)
        assertEquals("Por hacer", column.name)
        assertEquals(2, column.position)
        assertEquals(5, column.wipLimit)
        assertTrue(column.isDoneColumn)
        assertEquals(instant, column.sync.updatedAt)
        assertTrue(column.sync.pending)
        assertNull(column.sync.deletedAt)
        assertFalse(column.isDeleted)
    }

    @Test
    fun dominioAEntidad_escribeTodosLosCampos() {
        val updatedAt = instant.plusSeconds(10)
        val deletedAt = instant.plusSeconds(30)
        val column = Column(
            boardId = "tablero-1",
            name = "Hecho",
            position = 2,
            wipLimit = null,
            isDoneColumn = true,
            createdAt = Instant.EPOCH,
            sync = SyncMetadata(
                clientId = "uuid-1",
                updatedAt = updatedAt,
                pending = true,
                deletedAt = deletedAt,
            ),
        )

        val entity = column.toEntity()

        assertEquals("uuid-1", entity.id)
        assertEquals("tablero-1", entity.boardId)
        assertEquals("Hecho", entity.name)
        assertEquals(2, entity.position)
        assertNull(entity.wipLimit)
        assertTrue(entity.isDoneColumn)
        assertEquals(updatedAt.toEpochMilli(), entity.updatedAt)
        assertTrue(entity.pending)
        assertEquals(deletedAt.toEpochMilli(), entity.deletedAt)
    }

    @Test
    fun wipLimit_admiteNuloYPresente() {
        val withLimit = column(wipLimit = 7).toEntity().toColumn()
        val withoutLimit = column(wipLimit = null).toEntity().toColumn()

        assertEquals(7, withLimit.wipLimit)
        assertNull(withoutLimit.wipLimit)
    }

    @Test
    fun idaYVuelta_preservaElInstanteYLosMetadatos() {
        val column = column(
            sync = SyncMetadata(
                clientId = "uuid-cliente",
                updatedAt = instant.plusSeconds(5),
                pending = true,
                deletedAt = instant.plusSeconds(9),
            ),
        )

        val restored = column.toEntity().toColumn()

        assertEquals(column, restored)
    }

    private fun column(
        wipLimit: Int? = null,
        sync: SyncMetadata = SyncMetadata(
            clientId = "uuid-1",
            updatedAt = instant,
            pending = true,
            deletedAt = null,
        ),
    ): Column = Column(
        boardId = "tablero-1",
        name = "En progreso",
        position = 1,
        wipLimit = wipLimit,
        isDoneColumn = false,
        createdAt = Instant.EPOCH,
        sync = sync,
    )
}
