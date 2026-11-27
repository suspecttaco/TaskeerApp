package com.simposio.taskeer.data.remote.sync

import com.simposio.taskeer.data.remote.ApiClient
import com.simposio.taskeer.data.sync.SyncTransport

object BridgeTransportFactory {
    fun create(client: ApiClient, accessToken: () -> String?): SyncTransport? = RestBridgeTransport(client, accessToken)
}
