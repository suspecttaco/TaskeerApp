package com.simposio.taskeer.domain.sync

import java.time.Duration
import java.time.Instant

data class ServerVersion(val updatedAt: Instant, val deleted: Boolean)

data class LocalVersion(val updatedAt: Instant, val deleted: Boolean)

sealed interface ConflictVerdict {
    data object LocalWins : ConflictVerdict

    data object ServerWins : ConflictVerdict
}

// Misma regla que el servidor (contrato 1.0, sección 4); los vectores V07-V13 deben coincidir en ambos lados.
object ConflictRules {
    val CLOCK_SKEW_LIMIT: Duration = Duration.ofMinutes(5)

    fun resolve(base: Instant?, server: ServerVersion, local: LocalVersion, now: Instant): ConflictVerdict {
        if (base != null && !server.updatedAt.isAfter(base)) return ConflictVerdict.LocalWins
        if (server.deleted && !local.deleted) return ConflictVerdict.ServerWins
        val localClock = minOf(local.updatedAt, now + CLOCK_SKEW_LIMIT)
        return if (localClock.isAfter(server.updatedAt)) ConflictVerdict.LocalWins else ConflictVerdict.ServerWins
    }
}
