package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.sync.PauseReason
import java.time.Duration

sealed interface Acquisition {
    class Ready(val transport: SyncTransport, val identity: Identity) : Acquisition

    class Paused(val reason: PauseReason) : Acquisition

    class Retry(val after: Duration?) : Acquisition

    data object Unauthorized : Acquisition
}

// Decide qué transporte usa el ciclo y cuál es la cuenta: el motor no sabe de dónde viene ni cuántos hay.
interface TransportSource {
    suspend fun acquire(): Acquisition

    // Olvida el sondeo: se repite en el próximo ciclo (cambio de sesión o ruta aún no disponible).
    fun invalidate()
}
