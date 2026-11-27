package com.simposio.taskeer.data.repository

// El rol efectivo llega por sincronización (`boardAccess`): un cambio de rol en vivo dispara una traída para aplicarlo al
// kanban local, además de la relectura que ya hace la pantalla de roles.
class RoleSignalSync(
    private val signals: RoleChangeSignals,
    private val sync: SyncRepository,
) {
    suspend fun run() {
        signals.signals.collect { sync.requestSync() }
    }
}
