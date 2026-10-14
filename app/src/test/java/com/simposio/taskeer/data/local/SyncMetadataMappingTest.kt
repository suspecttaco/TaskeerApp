package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.SyncMetadata
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMetadataMappingTest {

    private val instant = Instant.ofEpochMilli(1_721_000_000_123L)

    private fun board(
        id: String = "id-1",
        updatedAt: Long = instant.toEpochMilli(),
        pending: Boolean = true,
        deletedAt: Long? = null,
    ) = BoardEntity(id = id, name = "tablero", updatedAt = updatedAt, pending = pending, deletedAt = deletedAt)

    @Test
    fun entidadADominio_preservaIdYUpdatedAt() {
        val metadata = board(id = "uuid-cliente").toSyncMetadata()

        assertEquals("uuid-cliente", metadata.clientId)
        assertEquals(instant, metadata.updatedAt)
    }

    @Test
    fun idaYVuelta_updatedAtPreservaElInstante() {
        val original = board(updatedAt = instant.toEpochMilli())

        val restored = original.withSyncMetadata(original.toSyncMetadata())

        assertEquals(instant.toEpochMilli(), restored.updatedAt)
        assertEquals(instant, restored.toSyncMetadata().updatedAt)
    }

    @Test
    fun deletedAtNulo_mapeaActiva() {
        val metadata = board(deletedAt = null).toSyncMetadata()

        assertNull(metadata.deletedAt)
        assertFalse(metadata.isDeleted)
    }

    @Test
    fun deletedAtNoNulo_mapeaBorrada() {
        val deletedAt = Instant.ofEpochMilli(1_721_000_000_999L)

        val metadata = board(deletedAt = deletedAt.toEpochMilli()).toSyncMetadata()

        assertEquals(deletedAt, metadata.deletedAt)
        assertTrue(metadata.isDeleted)
    }

    @Test
    fun withSyncMetadata_escribeLosCuatroCampos() {
        val metadata = SyncMetadata(
            clientId = "uuid-nuevo",
            updatedAt = Instant.ofEpochMilli(9_000L),
            pending = true,
            deletedAt = Instant.ofEpochMilli(9_500L),
        )

        val result = board(id = "otro", updatedAt = 1L, pending = false, deletedAt = null).withSyncMetadata(metadata)

        assertEquals("uuid-nuevo", result.id)
        assertEquals(9_000L, result.updatedAt)
        assertTrue(result.pending)
        assertEquals(9_500L, result.deletedAt)

        val active = result.withSyncMetadata(metadata.copy(deletedAt = null))

        assertNull(active.deletedAt)
        assertTrue(active.pending)
    }
}
