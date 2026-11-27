package com.simposio.taskeer.data.remote.supabase

import com.simposio.taskeer.data.remote.ApiConfig
import com.simposio.taskeer.data.remote.FakeHttpTransport
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.remote.TransportFailureKind
import com.simposio.taskeer.data.remote.auth.GoogleExchangeResult
import com.simposio.taskeer.data.remote.failure
import com.simposio.taskeer.data.remote.response
import com.simposio.taskeer.domain.auth.GoogleCredential
import com.simposio.taskeer.domain.model.DomainError
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseTokenExchangeTest {

    private val transport = FakeHttpTransport()
    private val config = ApiConfig(baseUrl = "https://proyecto.supabase.test", allowCleartext = false, userAgent = "Taskeer-Android/1.0")
    private val exchange = SupabaseTokenExchange(transport, config, anonKey = "anon-key-secreta")
    private val credential = GoogleCredential("id-token-secreto", "nonce-claro")

    private fun failed(result: GoogleExchangeResult) = result as GoogleExchangeResult.Failed

    @Test
    fun ok_devuelveAccessToken() = runTest {
        transport.enqueue(response(200, """{"access_token":"jwt-supabase","refresh_token":"r","user":{}}"""))

        val result = exchange.exchange(credential)

        assertEquals("jwt-supabase", (result as GoogleExchangeResult.Token).accessToken)
    }

    @Test
    fun peticion_vaAlHostDeSupabaseConApikeySinAuthorizationNiXClient() = runTest {
        transport.enqueue(response(200, """{"access_token":"jwt"}"""))

        exchange.exchange(credential)

        val request = transport.requests.single()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("https://proyecto.supabase.test/auth/v1/token?grant_type=id_token", request.url)
        assertEquals("anon-key-secreta", request.headers["apikey"])
        assertNull(request.headers["Authorization"])
        assertNull(request.headers["X-Client"])
        assertEquals("application/json; charset=utf-8", request.headers["Content-Type"])
        assertEquals("application/json", request.headers["Accept"])
        val body = JSONObject(requireNotNull(request.body))
        assertEquals("google", body.getString("provider"))
        assertEquals("id-token-secreto", body.getString("id_token"))
        assertEquals("nonce-claro", body.getString("nonce"))
    }

    @Test
    fun badIdToken_esInvalidGoogleToken() = runTest {
        transport.enqueue(response(400, """{"error_code":"bad_id_token","msg":"x"}"""))

        val error = failed(exchange.exchange(credential)).error

        assertTrue(error is DomainError.Unauthenticated)
        assertEquals("INVALID_GOOGLE_TOKEN", error.code)
    }

    @Test
    fun nonceInvalido_esInvalidGoogleToken() = runTest {
        transport.enqueue(response(400, """{"error":"invalid_grant","error_description":"Nonces mismatch"}"""))

        assertEquals("INVALID_GOOGLE_TOKEN", failed(exchange.exchange(credential)).error.code)
    }

    @Test
    fun providerDisabled_esRechazado() = runTest {
        transport.enqueue(response(400, """{"error_code":"provider_disabled"}"""))

        val error = failed(exchange.exchange(credential)).error

        assertTrue(error is DomainError.Validation)
        assertEquals("GOOGLE_EXCHANGE_REJECTED", error.code)
    }

    @Test
    fun `429_esRateLimitedConRetryAfter`() = runTest {
        transport.enqueue(response(429, "", mapOf("retry-after" to "12")))

        val result = failed(exchange.exchange(credential))

        assertTrue(result.error is DomainError.RateLimited)
        assertEquals(12L, result.retryAfterSeconds)
    }

    @Test
    fun `5xx_esServer`() = runTest {
        transport.enqueue(response(502, "<html>"))

        assertTrue(failed(exchange.exchange(credential)).error is DomainError.Server)
    }

    @Test
    fun sinRed_esNetwork() = runTest {
        transport.enqueue(failure(TransportFailureKind.UNREACHABLE))

        assertEquals("NETWORK_UNREACHABLE", failed(exchange.exchange(credential)).error.code)
    }

    @Test
    fun timeout_esNetwork() = runTest {
        transport.enqueue(failure(TransportFailureKind.TIMEOUT))

        val error = failed(exchange.exchange(credential)).error

        assertTrue(error is DomainError.Network)
        assertEquals("NETWORK_TIMEOUT", error.code)
    }

    @Test
    fun `200SinAccessToken_esRespuestaInvalida`() = runTest {
        transport.enqueue(response(200, """{"refresh_token":"r"}"""))

        val error = failed(exchange.exchange(credential)).error

        assertTrue(error is DomainError.Server)
        assertEquals("INVALID_LOGIN_RESPONSE", error.code)
    }

    @Test
    fun nuncaUsaElMensajeDelServidor() = runTest {
        transport.enqueue(response(400, """{"error_code":"bad_id_token","msg":"MENSAJE-DEL-SERVIDOR"}"""))

        val error = failed(exchange.exchange(credential)).error

        assertFalse(error.message.contains("MENSAJE-DEL-SERVIDOR"))
    }

    @Test
    fun noReintentaAutomaticamente() = runTest {
        transport.enqueue(response(500, ""))

        exchange.exchange(credential)

        assertEquals(1, transport.requests.size)
    }
}
