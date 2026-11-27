package com.simposio.taskeer.domain.sync

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ConflictRulesTest {

    private val now = Instant.parse("2026-11-26T18:30:00Z")
    private val base = Instant.parse("2026-11-26T10:00:00Z")

    private fun resolve(
        base: Instant?,
        server: Instant,
        local: Instant,
        serverDeleted: Boolean = false,
        localDeleted: Boolean = false,
    ) = ConflictRules.resolve(base, ServerVersion(server, serverDeleted), LocalVersion(local, localDeleted), now)

    @Test
    fun sinCambioDeVersion_ganaLocalSinComparar() {
        val verdict = resolve(base, server = base, local = Instant.parse("2026-11-26T09:00:00Z"))

        assertEquals(ConflictVerdict.LocalWins, verdict)
    }

    @Test
    fun tombstoneServidor_ganaServidor() {
        val verdict = resolve(
            base,
            server = Instant.parse("2026-11-26T12:00:00Z"),
            local = Instant.parse("2026-11-26T18:00:00Z"),
            serverDeleted = true,
        )

        assertEquals(ConflictVerdict.ServerWins, verdict)
    }

    @Test
    fun ediciones_gana_laMasReciente() {
        val server = Instant.parse("2026-11-26T12:00:00Z")

        assertEquals(ConflictVerdict.LocalWins, resolve(base, server, Instant.parse("2026-11-26T13:00:00Z")))
        assertEquals(ConflictVerdict.ServerWins, resolve(base, server, Instant.parse("2026-11-26T11:00:00Z")))
    }

    @Test
    fun empate_ganaServidor() {
        val server = Instant.parse("2026-11-26T12:00:00Z")

        assertEquals(ConflictVerdict.ServerWins, resolve(base, server, server))
    }

    @Test
    fun borradoLocalMasReciente_ganaLocal() {
        val verdict = resolve(
            base,
            server = Instant.parse("2026-11-26T12:00:00Z"),
            local = Instant.parse("2026-11-26T13:00:00Z"),
            localDeleted = true,
        )

        assertEquals(ConflictVerdict.LocalWins, verdict)
    }

    @Test
    fun relojAdelantado_seAcotaACincoMinutos() {
        val future = Instant.parse("2027-01-01T00:00:00Z")

        assertEquals(ConflictVerdict.LocalWins, resolve(base, Instant.parse("2026-11-26T18:34:00Z"), future))
        assertEquals(ConflictVerdict.ServerWins, resolve(base, Instant.parse("2026-11-26T18:40:00Z"), future))
    }

    @Test
    fun altaSinBaseSobreRegistroExistente_comparaRelojes() {
        val server = Instant.parse("2026-11-26T12:00:00Z")

        assertEquals(ConflictVerdict.ServerWins, resolve(null, server, Instant.parse("2026-11-26T11:00:00Z")))
        assertEquals(ConflictVerdict.LocalWins, resolve(null, server, Instant.parse("2026-11-26T12:00:00.001Z")))
    }
}
