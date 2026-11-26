package com.simposio.taskeer.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiConfigTest {

    @Test
    fun release_conHttp_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiConfig("http://api.test/v1/", allowCleartext = false, userAgent = "ua")
        }
    }

    @Test
    fun debug_conHttp_seAcepta() {
        val config = ApiConfig("http://10.0.2.2:3100/api/v1/", allowCleartext = true, userAgent = "ua")

        assertEquals("http://10.0.2.2:3100/api/v1/", config.baseUrl)
    }

    @Test
    fun baseUrl_sinBarraFinal_seNormaliza() {
        val config = ApiConfig("https://api.test/v1", allowCleartext = false, userAgent = "ua")

        assertEquals("https://api.test/v1/", config.baseUrl)
    }

    @Test
    fun timeoutsPorDefecto_son15_45_30_60Segundos() {
        val config = ApiConfig("https://api.test/v1/", allowCleartext = false, userAgent = "ua")

        assertEquals(
            listOf(15_000L, 45_000L, 30_000L, 60_000L),
            listOf(config.connectTimeoutMs, config.readTimeoutMs, config.writeTimeoutMs, config.callTimeoutMs),
        )
    }
}
