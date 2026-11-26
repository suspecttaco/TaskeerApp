package com.simposio.taskeer.data.remote

import android.content.Context
import com.simposio.taskeer.BuildConfig
import com.simposio.taskeer.data.remote.session.AndroidKeystoreTokenCipher
import com.simposio.taskeer.data.remote.session.AuthSession
import com.simposio.taskeer.data.remote.session.EncryptedRefreshTokenStore
import com.simposio.taskeer.data.remote.session.HttpSessionRefresher
import com.simposio.taskeer.data.remote.session.SharedPreferencesKeyValueStore
import com.simposio.taskeer.data.remote.transport.OkHttpStreamTransport
import com.simposio.taskeer.data.remote.transport.OkHttpTransport

class RemoteApi(val client: ApiClient, val session: AuthSession, val streamTransport: StreamTransport, val config: ApiConfig)

fun createApiClient(context: Context): RemoteApi {
    val config = ApiConfig(
        baseUrl = BuildConfig.API_BASE_URL,
        allowCleartext = BuildConfig.DEBUG,
        userAgent = "Taskeer-Android/${BuildConfig.VERSION_NAME}",
    )
    val transport = OkHttpTransport(config)
    val store = EncryptedRefreshTokenStore(
        keyValueStore = SharedPreferencesKeyValueStore(context),
        cipher = AndroidKeystoreTokenCipher(),
    )
    val session = AuthSession(store, HttpSessionRefresher(transport, config))
    return RemoteApi(RealApiClient(transport, session, config), session, OkHttpStreamTransport(config), config)
}
