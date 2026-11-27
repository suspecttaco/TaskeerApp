package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.sync.PauseReason

// Un solo transporte por ciclo y por sesión: el contrato 1.0 o una pausa «servidor no preparado».
// El sondeo es el propio identify: nunca hay petición extra.
class SyncTransportSelector(private val contract: SyncTransport) : TransportSource {

    private var cached: Acquisition.Ready? = null

    override suspend fun acquire(): Acquisition {
        cached?.let { return it }
        val acquisition = probeContract()
        if (acquisition is Acquisition.Ready) cached = acquisition
        return acquisition
    }

    override fun invalidate() {
        cached = null
    }

    private suspend fun probeContract(): Acquisition = when (val result = contract.identify()) {
        is TransportResult.Ok ->
            if (result.value.supportedEntities.isEmpty()) Acquisition.Paused(PauseReason.SERVER_NOT_READY)
            else Acquisition.Ready(contract, result.value)
        TransportResult.NotAvailable -> Acquisition.Paused(PauseReason.SERVER_NOT_READY)
        TransportResult.UpdateRequired -> Acquisition.Paused(PauseReason.UPDATE_REQUIRED)
        is TransportResult.Retry -> Acquisition.Retry(result.after)
        TransportResult.Unauthorized -> Acquisition.Unauthorized
        is TransportResult.Invalid -> Acquisition.Retry(null)
    }
}
