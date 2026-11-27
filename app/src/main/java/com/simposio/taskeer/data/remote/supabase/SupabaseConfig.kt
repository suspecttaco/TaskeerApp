package com.simposio.taskeer.data.remote.supabase

import com.simposio.taskeer.data.remote.ApiConfig
import com.simposio.taskeer.data.remote.HttpTransport
import com.simposio.taskeer.data.remote.auth.GoogleTokenExchange

// Sin configuración válida (falta algo, o http fuera de debug) el canje no existe y el botón de Google no se ofrece.
fun createSupabaseExchange(
    url: String,
    anonKey: String,
    allowCleartext: Boolean,
    httpLogging: Boolean,
    userAgent: String,
    transportFor: (ApiConfig) -> HttpTransport,
): GoogleTokenExchange? {
    if (url.isBlank() || anonKey.isBlank()) return null
    val config = try {
        ApiConfig(baseUrl = url, allowCleartext = allowCleartext, userAgent = userAgent, httpLogging = httpLogging)
    } catch (_: IllegalArgumentException) {
        return null
    }
    return SupabaseTokenExchange(transportFor(config), config, anonKey)
}
