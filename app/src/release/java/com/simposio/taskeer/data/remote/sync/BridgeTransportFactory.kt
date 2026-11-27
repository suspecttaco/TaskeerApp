package com.simposio.taskeer.data.remote.sync

import com.simposio.taskeer.data.remote.ApiClient
import com.simposio.taskeer.data.sync.SyncTransport

// El puente REST solo existe en depuración: la versión publicada habla únicamente el contrato 1.0.
object BridgeTransportFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(client: ApiClient, accessToken: () -> String?): SyncTransport? = null
}
