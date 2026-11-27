package com.simposio.taskeer.data.remote.supabase

import com.simposio.taskeer.data.remote.ApiConfig
import com.simposio.taskeer.data.remote.FakeHttpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SupabaseConfigTest {

    private fun create(url: String, key: String, cleartext: Boolean = false, configs: MutableList<ApiConfig> = mutableListOf()) =
        createSupabaseExchange(url, key, cleartext, httpLogging = false, userAgent = "t") { config ->
            configs += config
            FakeHttpTransport()
        }

    @Test
    fun sinSupabaseUrl_noDisponible() {
        assertNull(create("", "anon"))
        assertNull(create("   ", "anon"))
    }

    @Test
    fun sinAnonKey_noDisponible() {
        assertNull(create("https://p.supabase.test", ""))
    }

    @Test
    fun completa_disponibleYUsaSuPropioApiConfig() {
        val configs = mutableListOf<ApiConfig>()

        assertNotNull(create("https://p.supabase.test", "anon", configs = configs))
        assertEquals("https://p.supabase.test/", configs.single().baseUrl)
    }

    @Test
    fun urlHttpEnRelease_noDisponible() {
        assertNull(create("http://10.0.2.2:54321", "anon", cleartext = false))
    }

    @Test
    fun urlHttpEnDebug_disponible() {
        assertNotNull(create("http://10.0.2.2:54321", "anon", cleartext = true))
    }

    @Test
    fun urlSinEsquema_noDisponible() {
        assertNull(create("p.supabase.test", "anon"))
    }
}
