package com.simposio.taskeer.data.remote.session

import com.simposio.taskeer.data.remote.FakeHttpTransport
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.remote.TEST_CONFIG
import com.simposio.taskeer.data.remote.TransportFailureKind
import com.simposio.taskeer.data.remote.errorBody
import com.simposio.taskeer.data.remote.failure
import com.simposio.taskeer.data.remote.response
import com.simposio.taskeer.domain.model.DomainError
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpSessionRefresherTest {

    private val transport = FakeHttpTransport()
    private val refresher = HttpSessionRefresher(transport, TEST_CONFIG)

    private fun refresh(token: String = "r0"): RefreshOutcome = runBlocking { refresher.refresh(token) }

    private fun unavailable(outcome: RefreshOutcome) = outcome as RefreshOutcome.Unavailable

    @Test
    fun refresh_envia_postAuthRefresh_conXClientYCuerpo() {
        transport.enqueue(response(200, "{\"accessToken\":\"a\",\"refreshToken\":\"r\"}"))

        refresh("r-viejo")

        val sent = transport.requests.single()
        assertEquals(HttpMethod.POST, sent.method)
        assertEquals("https://api.test/api/v1/auth/refresh", sent.url)
        assertEquals("mobile", sent.headers["X-Client"])
        assertEquals("r-viejo", JSONObject(requireNotNull(sent.body)).getString("refreshToken"))
    }

    @Test
    fun respuesta200_devuelveRenewedConAmbosTokens() {
        transport.enqueue(response(200, "{\"accessToken\":\"a1\",\"refreshToken\":\"r1\"}"))

        assertEquals(RefreshOutcome.Renewed("a1", "r1"), refresh())
    }

    @Test
    fun respuesta200_sinRefreshToken_esUnavailable() {
        transport.enqueue(response(200, "{\"accessToken\":\"a1\"}"))

        assertTrue(refresh() is RefreshOutcome.Unavailable)
    }

    @Test
    fun invalidRefreshToken401_esRejected() {
        transport.enqueue(response(401, errorBody("INVALID_REFRESH_TOKEN")))

        assertTrue(refresh() is RefreshOutcome.Rejected)
    }

    @Test
    fun untrustedOrigin403_esUnavailableYNoCierraSesion() {
        transport.enqueue(response(403, errorBody("UNTRUSTED_ORIGIN")))

        assertTrue(refresh() is RefreshOutcome.Unavailable)
    }

    @Test
    fun sinRed_esUnavailableNetwork() {
        transport.enqueue(failure(TransportFailureKind.UNREACHABLE))

        val error = unavailable(refresh()).error

        assertEquals("NETWORK_UNREACHABLE", error.code)
    }

    @Test
    fun rateLimited429_esUnavailableConRetryAfter() {
        transport.enqueue(response(429, "Too many requests", mapOf("retry-after" to "20")))

        val outcome = unavailable(refresh())

        assertTrue(outcome.error is DomainError.RateLimited)
        assertEquals(20L, outcome.retryAfterSeconds)
    }

    @Test
    fun server500_esUnavailable() {
        transport.enqueue(response(500, errorBody("INTERNAL_SERVER_ERROR")))

        assertTrue(unavailable(refresh()).error is DomainError.Server)
    }

    @Test
    fun cuerpoIlegible_esUnavailable() {
        transport.enqueue(response(200, "<html>"))

        assertTrue(refresh() is RefreshOutcome.Unavailable)
    }

    @Test
    fun noUsaAuthorization() {
        transport.enqueue(response(200, "{\"accessToken\":\"a\",\"refreshToken\":\"r\"}"))

        refresh()

        assertFalse(transport.requests.single().headers.containsKey("Authorization"))
    }

    @Test
    fun tokenReutilizado401_esRejected() {
        transport.enqueue(response(401, errorBody("REFRESH_TOKEN_REUSED")))

        assertTrue(refresh() is RefreshOutcome.Rejected)
    }

    @Test
    fun tokenRevocado401_esRejected() {
        transport.enqueue(response(401, errorBody("REFRESH_TOKEN_REVOKED")))

        assertTrue(refresh() is RefreshOutcome.Rejected)
    }

    @Test
    fun unauthenticated401_esRejected() {
        transport.enqueue(response(401, errorBody("UNAUTHENTICATED")))

        assertTrue(refresh() is RefreshOutcome.Rejected)
    }

    @Test
    fun unauthorized401SinCodigoReconocible_esUnavailable() {
        transport.enqueue(response(401, ""))
        transport.enqueue(response(401, errorBody("ALGO_RARO")))

        assertTrue(refresh() is RefreshOutcome.Unavailable)
        assertTrue(refresh() is RefreshOutcome.Unavailable)
    }

    @Test
    fun timeout408_esUnavailable() {
        transport.enqueue(response(408, ""))

        assertTrue(refresh() is RefreshOutcome.Unavailable)
    }

    @Test
    fun corteDeConexion_esUnavailableYNoRejected() {
        transport.enqueue(failure(TransportFailureKind.IO))

        val outcome = refresh()

        assertTrue(outcome is RefreshOutcome.Unavailable)
        assertEquals("NETWORK_IO", (outcome as RefreshOutcome.Unavailable).error.code)
    }

    @Test
    fun enviaXClientMobileSinOrigin() {
        transport.enqueue(response(200, "{\"accessToken\":\"a\",\"refreshToken\":\"r\"}"))

        refresh()

        val headers = transport.requests.single().headers
        assertEquals("mobile", headers["X-Client"])
        assertFalse(headers.keys.any { it.equals("Origin", ignoreCase = true) })
    }

    @Test
    fun untrustedOrigin403_esUnavailableConMobileClientNotSupported() {
        transport.enqueue(response(403, errorBody("UNTRUSTED_ORIGIN")))

        val error = unavailable(refresh()).error

        assertEquals(DomainError.Forbidden::class, error::class)
        assertEquals("MOBILE_CLIENT_NOT_SUPPORTED", error.code)
    }
}
