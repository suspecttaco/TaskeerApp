package com.simposio.taskeer.data.remote

import android.content.Context
import com.simposio.taskeer.BuildConfig
import com.simposio.taskeer.data.remote.auth.GoogleTokenExchange
import com.simposio.taskeer.data.remote.session.AndroidKeystoreTokenCipher
import com.simposio.taskeer.data.remote.session.AuthSession
import com.simposio.taskeer.data.remote.session.EncryptedRefreshTokenStore
import com.simposio.taskeer.data.remote.session.HttpSessionRefresher
import com.simposio.taskeer.data.remote.session.SharedPreferencesKeyValueStore
import com.simposio.taskeer.data.remote.transport.OkHttpStreamTransport
import com.simposio.taskeer.data.remote.supabase.createSupabaseExchange
import com.simposio.taskeer.data.remote.transport.OkHttpTransport

class RemoteApi(
    val client: ApiClient,
    val session: AuthSession,
    val streamTransport: StreamTransport,
    val config: ApiConfig,
    val googleExchange: GoogleTokenExchange? = null,
)

fun createApiClient(context: Context): RemoteApi {
    val config = ApiConfig(
        baseUrl = BuildConfig.API_BASE_URL,
        allowCleartext = BuildConfig.DEBUG,
        httpLogging = BuildConfig.DEBUG,
        userAgent = "Taskeer-Android/${BuildConfig.VERSION_NAME}",
    )
    val transport = OkHttpTransport(config)
    val store = EncryptedRefreshTokenStore(
        keyValueStore = SharedPreferencesKeyValueStore(context),
        cipher = AndroidKeystoreTokenCipher(),
    )
    val session = AuthSession(store, HttpSessionRefresher(transport, config))
    // Segundo transporte con su propia configuración: Supabase es otro host y no comparte la base de Taskeer.
    val googleExchange = createSupabaseExchange(
        url = BuildConfig.SUPABASE_URL,
        anonKey = BuildConfig.SUPABASE_ANON_KEY,
        allowCleartext = BuildConfig.DEBUG,
        httpLogging = BuildConfig.DEBUG,
        userAgent = config.userAgent,
        transportFor = ::OkHttpTransport,
    )
    return RemoteApi(RealApiClient(transport, session, config), session, OkHttpStreamTransport(config), config, googleExchange)
}
