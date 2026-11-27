package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.sync.PauseReason

enum class TransportMode { AUTO, BRIDGE, B7 }

// Un solo transporte por ciclo y por sesión: contrato 1.0 si el servidor lo implementa; si no, el puente de depuración
// (si existe en esta variante) o una pausa «servidor no preparado». El sondeo es el propio identify: nunca hay petición extra.
class SyncTransportSelector(
    private val contract: SyncTransport,
    private val bridge: SyncTransport?,
    private val mode: TransportMode = TransportMode.AUTO,
) : TransportSource {

    private var cached: Acquisition.Ready? = null

    override suspend fun acquire(): Acquisition {
        cached?.let { return it }
        val acquisition = when {
            mode == TransportMode.BRIDGE && bridge != null -> use(bridge)
            else -> probeContract()
        }
        if (acquisition is Acquisition.Ready) cached = acquisition
        return acquisition
    }

    override fun invalidate() {
        cached = null
    }

    private suspend fun probeContract(): Acquisition = when (val result = contract.identify()) {
        is TransportResult.Ok ->
            if (result.value.supportedEntities.isEmpty()) fallback() else Acquisition.Ready(contract, result.value)
        TransportResult.NotAvailable -> fallback()
        TransportResult.UpdateRequired -> Acquisition.Paused(PauseReason.UPDATE_REQUIRED)
        is TransportResult.Retry -> Acquisition.Retry(result.after)
        TransportResult.Unauthorized -> Acquisition.Unauthorized
        is TransportResult.Invalid -> Acquisition.Retry(null)
    }

    private suspend fun fallback(): Acquisition =
        if (bridge == null || mode == TransportMode.B7) Acquisition.Paused(PauseReason.SERVER_NOT_READY) else use(bridge)

    private suspend fun use(transport: SyncTransport): Acquisition = when (val result = transport.identify()) {
        is TransportResult.Ok -> Acquisition.Ready(transport, result.value)
        TransportResult.NotAvailable -> Acquisition.Paused(PauseReason.SERVER_NOT_READY)
        TransportResult.UpdateRequired -> Acquisition.Paused(PauseReason.UPDATE_REQUIRED)
        is TransportResult.Retry -> Acquisition.Retry(result.after)
        TransportResult.Unauthorized -> Acquisition.Unauthorized
        is TransportResult.Invalid -> Acquisition.Retry(null)
    }
}
