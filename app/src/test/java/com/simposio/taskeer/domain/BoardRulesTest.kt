package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.board.newBoard
import com.simposio.taskeer.domain.board.renamed
import com.simposio.taskeer.domain.board.softDeleted
import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.SyncMetadata
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardRulesTest {

    private val now = Instant.parse("2026-10-14T10:00:00Z")

    @Test
    fun board_exponeElIdYElEstadoDesdeLosMetadatos() {
        val sync = SyncMetadata(
            clientId = "uuid-cliente",
            updatedAt = now,
            pending = false,
            deletedAt = now.plusSeconds(1),
        )

        val board = Board(name = "Tablero", createdAt = now, sync = sync)

        assertEquals("uuid-cliente", board.id)
        assertTrue(board.isDeleted)
    }

    @Test
    fun newBoard_creaElTableroRecortadoYPendiente() {
        val result = newBoard("  Compras  ", now, clientId = "uuid-1")

        val board = (result as DomainResult.Success).value

        assertEquals("Compras", board.name)
        assertEquals(now, board.createdAt)
        assertEquals("uuid-1", board.id)
        assertEquals(now, board.sync.updatedAt)
        assertTrue(board.sync.pending)
        assertNull(board.sync.deletedAt)
    }

    @Test
    fun newBoard_idsDistintosPorLlamada() {
        val first = (newBoard("Compras", now) as DomainResult.Success).value
        val second = (newBoard("Compras", now) as DomainResult.Success).value

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun newBoard_sinNombre_fallaConValidation() {
        listOf("", "   ").forEach { name ->
            val result = newBoard(name, now)

            val error = (result as DomainResult.Failure).error

            assertTrue(error is DomainError.Validation)
            assertEquals("BOARD_NAME_REQUIRED", error.code)
        }
    }

    @Test
    fun newBoard_longitudesDelNombre() {
        fun code(length: Int): String? =
            (newBoard("x".repeat(length), now) as? DomainResult.Failure)?.error?.code

        assertEquals("BOARD_NAME_REQUIRED", code(0))
        assertEquals("BOARD_NAME_TOO_SHORT", code(1))
        assertNull(code(2))
        assertNull(code(150))
        assertEquals("BOARD_NAME_TOO_LONG", code(151))
    }

    @Test
    fun newBoard_longitudSeMideTrasRecortar() {
        assertNull((newBoard("  " + "x".repeat(150) + "  ", now) as? DomainResult.Failure))
        assertEquals(
            "BOARD_NAME_TOO_SHORT",
            (newBoard("  x  ", now) as DomainResult.Failure).error.code,
        )
    }

    @Test
    fun newBoard_mensajesEnEspanolConElLimite() {
        val short = (newBoard("x", now) as DomainResult.Failure).error
        val long = (newBoard("x".repeat(151), now) as DomainResult.Failure).error

        assertTrue(short is DomainError.Validation)
        assertEquals("El nombre del tablero debe tener al menos 2 caracteres", short.message)
        assertEquals("El nombre del tablero no puede superar los 150 caracteres", long.message)
    }

    @Test
    fun renamed_aplicaLosMismosLimitesQueElAlta() {
        val board = (newBoard("Compras", now) as DomainResult.Success).value

        assertEquals("BOARD_NAME_TOO_SHORT", (board.renamed("x", now) as DomainResult.Failure).error.code)
        assertEquals("BOARD_NAME_TOO_LONG", (board.renamed("x".repeat(151), now) as DomainResult.Failure).error.code)
        assertTrue(board.renamed("xx", now) is DomainResult.Success)
    }

    @Test
    fun renamed_cambiaElNombreYNoTocaIdNiCreatedAt() {
        val board = (newBoard("Compras", now, clientId = "uuid-1") as DomainResult.Success).value
        val later = now.plusSeconds(60)

        val renamed = (board.renamed("  Mercado  ", later) as DomainResult.Success).value

        assertEquals("Mercado", renamed.name)
        assertEquals(later, renamed.sync.updatedAt)
        assertTrue(renamed.sync.pending)
        assertEquals("uuid-1", renamed.id)
        assertEquals(now, renamed.createdAt)
        assertNull(renamed.sync.deletedAt)
    }

    @Test
    fun renamed_sinNombre_fallaConValidation() {
        val board = (newBoard("Compras", now) as DomainResult.Success).value

        val result = board.renamed("   ", now.plusSeconds(60))

        val error = (result as DomainResult.Failure).error
        assertTrue(error is DomainError.Validation)
        assertEquals("BOARD_NAME_REQUIRED", error.code)
    }

    @Test
    fun softDeleted_marcaDeletedAtSinCambiarElResto() {
        val board = (newBoard("Compras", now, clientId = "uuid-1") as DomainResult.Success).value
        val later = now.plusSeconds(120)

        val deleted = board.softDeleted(later)

        assertEquals(later, deleted.sync.deletedAt)
        assertEquals(later, deleted.sync.updatedAt)
        assertTrue(deleted.sync.pending)
        assertEquals("uuid-1", deleted.id)
        assertEquals("Compras", deleted.name)
        assertEquals(now, deleted.createdAt)
    }
}
