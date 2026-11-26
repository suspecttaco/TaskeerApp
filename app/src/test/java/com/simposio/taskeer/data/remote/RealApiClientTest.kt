package com.simposio.taskeer.data.remote

import com.simposio.taskeer.data.remote.session.AuthSession
import com.simposio.taskeer.data.remote.session.HttpSessionRefresher
import com.simposio.taskeer.data.remote.session.RefreshOutcome
import com.simposio.taskeer.domain.model.DomainError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RealApiClientTest {

    private val transport = FakeHttpTransport()
    private val store = InMemoryRefreshTokenStore("r0")
    private val refresher = FakeSessionRefresher()
    private val session = AuthSession(store, refresher)
    private val client = RealApiClient(transport, session, TEST_CONFIG)

    private val get = ApiRequest(HttpMethod.GET, "tasks/1")
    private val unauthenticated = response(401, errorBody("UNAUTHENTICATED"))

    private class Counter(var value: Int = 0)

    private fun TestScope.countExpirations(target: AuthSession): Counter {
        val counter = Counter()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            target.expirations.collect { counter.value++ }
        }
        return counter
    }

    private fun failure(result: ApiResult) = (result as ApiResult.Failure)

    @Test
    fun rutaConEsquema_seRechazaSinSalirALaRed() = runTest {
        listOf("https://evil.test/x", "http://evil.test/x", "//evil.test/x").forEach { path ->
            val result = failure(client.execute(ApiRequest(HttpMethod.GET, path)))

            assertEquals("INVALID_REQUEST_PATH", result.error.code)
            assertTrue(result.error is DomainError.Validation)
        }
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun rutaDobleBarra_seRechaza() = runTest {
        val result = failure(client.execute(ApiRequest(HttpMethod.GET, "//evil.test")))

        assertEquals("INVALID_REQUEST_PATH", result.error.code)
    }

    @Test
    fun enviaCabecerasFijasYUserAgent() = runTest {
        transport.enqueue(response(200, "{}"))

        client.execute(ApiRequest(HttpMethod.POST, "tasks", jsonBody = "{}"))

        val sent = transport.requests.single()
        assertEquals("https://api.test/api/v1/tasks", sent.url)
        assertEquals("application/json", sent.headers["Accept"])
        assertEquals("mobile", sent.headers["X-Client"])
        assertEquals("Taskeer-Android/1.2.3", sent.headers["User-Agent"])
        assertEquals("application/json; charset=utf-8", sent.headers["Content-Type"])
    }

    @Test
    fun sinCuerpo_noEnviaContentType() = runTest {
        transport.enqueue(response(200, "{}"))

        client.execute(get)

        assertFalse(transport.requests.single().headers.containsKey("Content-Type"))
    }

    @Test
    fun consulta_seCodificaEnLaUrl() = runTest {
        transport.enqueue(response(200, "{}"))

        client.execute(ApiRequest(HttpMethod.GET, "/tasks", query = listOf("q" to "a b&c", "n" to "1")))

        assertEquals("https://api.test/api/v1/tasks?q=a%20b%26c&n=1", transport.requests.single().url)
    }

    @Test
    fun bearerConToken_anadeAuthorization() = runTest {
        session.start("a0", "r0")
        transport.enqueue(response(200, "{}"))

        client.execute(get)

        assertEquals("Bearer a0", transport.requests.single().headers["Authorization"])
    }

    @Test
    fun bearerSinToken_noAnadeAuthorization() = runTest {
        transport.enqueue(response(200, "{}"))

        client.execute(get)

        assertFalse(transport.requests.single().headers.containsKey("Authorization"))
    }

    @Test
    fun authNone_nuncaAnadeAuthorization() = runTest {
        session.start("a0", "r0")
        transport.enqueue(response(200, "{}"))

        client.execute(ApiRequest(HttpMethod.GET, "health", auth = AuthMode.NONE))

        assertFalse(transport.requests.single().headers.containsKey("Authorization"))
    }

    @Test
    fun get200_devuelveSuccessConCuerpo() = runTest {
        transport.enqueue(response(200, "{\"id\":1}"))

        val result = client.execute(get) as ApiResult.Success

        assertEquals(200, result.response.status)
        assertEquals("{\"id\":1}", result.response.body)
    }

    @Test
    fun delete204_devuelveSuccessVacio() = runTest {
        transport.enqueue(response(204))

        val result = client.execute(ApiRequest(HttpMethod.DELETE, "tasks/1")) as ApiResult.Success

        assertEquals(204, result.response.status)
        assertEquals("", result.response.body)
    }

    @Test
    fun unauthenticated_renuevaYReintentaUnaVez() = runTest {
        session.start("a0", "r0")
        transport.enqueue(unauthenticated)
        transport.enqueue(response(200, "{\"ok\":true}"))
        refresher.enqueue(RefreshOutcome.Renewed("a1", "r1"))

        val result = client.execute(get)

        assertTrue(result is ApiResult.Success)
        assertEquals(listOf("Bearer a0", "Bearer a1"), transport.requests.map { it.headers["Authorization"] })
        assertEquals(1, refresher.receivedTokens.size)
    }

    @Test
    fun reintentoConOtro401_noRenuevaDeNuevo() = runTest {
        session.start("a0", "r0")
        transport.enqueue(unauthenticated)
        transport.enqueue(unauthenticated)
        refresher.enqueue(RefreshOutcome.Renewed("a1", "r1"))

        val result = failure(client.execute(get))

        assertTrue(result.error is DomainError.Unauthenticated)
        assertEquals(2, transport.requests.size)
        assertEquals(1, refresher.receivedTokens.size)
    }

    @Test
    fun cincoPeticionesConcurrentesCon401_unSoloRefresh() = runTest {
        session.start("a0", "r0")
        transport.fallback = { request ->
            if (request.headers["Authorization"] == "Bearer a1") response(200, "{}") else unauthenticated
        }
        val gate = CompletableDeferred<Unit>().also { refresher.gate = it }
        refresher.enqueue(RefreshOutcome.Renewed("a1", "r1"))
        val calls = (1..5).map { async { client.execute(get) } }
        advanceUntilIdle()

        gate.complete(Unit)
        val results = calls.awaitAll()

        assertEquals(1, refresher.receivedTokens.size)
        assertTrue(results.all { it is ApiResult.Success })
    }

    @Test
    fun invalidCredentials401_noRenueva() = runTest {
        session.start("a0", "r0")
        transport.enqueue(response(401, errorBody("INVALID_CREDENTIALS")))

        val result = failure(client.execute(get))

        assertEquals("INVALID_CREDENTIALS", result.error.code)
        assertTrue(refresher.receivedTokens.isEmpty())
    }

    @Test
    fun authNone401_noRenueva() = runTest {
        transport.enqueue(unauthenticated)

        client.execute(ApiRequest(HttpMethod.POST, "auth/login", jsonBody = "{}", auth = AuthMode.NONE))

        assertTrue(refresher.receivedTokens.isEmpty())
    }

    @Test
    fun sinCuerpo401_noRenueva() = runTest {
        session.start("a0", "r0")
        transport.enqueue(response(401, ""))

        val result = failure(client.execute(get))

        assertTrue(result.error is DomainError.Unauthenticated)
        assertTrue(refresher.receivedTokens.isEmpty())
    }

    @Test
    fun refreshRechazado_devuelveUnauthenticadoYLimpiaSesion() = runTest {
        session.start("a0", "r0")
        val expirations = countExpirations(session)
        transport.enqueue(unauthenticated)
        refresher.enqueue(RefreshOutcome.Rejected(DomainError.Unauthenticated("INVALID_REFRESH_TOKEN", "x")))

        val result = failure(client.execute(get))

        assertTrue(result.error is DomainError.Unauthenticated)
        assertNull(store.value)
        assertNull(session.currentAccessToken())
        assertEquals(1, expirations.value)
    }

    @Test
    fun refreshSinRed_devuelveNetworkSinLimpiarSesion() = runTest {
        session.start("a0", "r0")
        transport.enqueue(unauthenticated)
        refresher.enqueue(RefreshOutcome.Unavailable(DomainError.Network("NETWORK_UNREACHABLE", "x")))

        val result = failure(client.execute(get))

        assertEquals("NETWORK_UNREACHABLE", result.error.code)
        assertEquals("r0", store.value)
        assertEquals("a0", session.currentAccessToken())
    }

    @Test
    fun refreshConRateLimit_propagaRetryAfter() = runTest {
        session.start("a0", "r0")
        transport.enqueue(unauthenticated)
        refresher.enqueue(
            RefreshOutcome.Unavailable(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), retryAfterSeconds = 40),
        )

        val result = failure(client.execute(get))

        assertEquals(40L, result.retryAfterSeconds)
    }

    @Test
    fun forbidden403_noRenueva() = runTest {
        session.start("a0", "r0")
        transport.enqueue(response(403, errorBody("FORBIDDEN")))

        val result = failure(client.execute(get))

        assertTrue(result.error is DomainError.Forbidden)
        assertTrue(refresher.receivedTokens.isEmpty())
    }

    @Test
    fun error500_noReintenta() = runTest {
        transport.enqueue(response(500, errorBody("INTERNAL_SERVER_ERROR")))

        val result = failure(client.execute(get))

        assertTrue(result.error is DomainError.Server)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun sinRed_devuelveNetwork_sinReintento() = runTest {
        transport.enqueue(failure(TransportFailureKind.UNREACHABLE))

        val result = failure(client.execute(get))

        assertEquals("NETWORK_UNREACHABLE", result.error.code)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun rateLimited429_devuelveRateLimitedConRetryAfter() = runTest {
        transport.enqueue(response(429, "Too many requests", mapOf("retry-after" to "15")))

        val result = failure(client.execute(get))

        assertTrue(result.error is DomainError.RateLimited)
        assertEquals(15L, result.retryAfterSeconds)
    }

    @Test
    fun cancelacion_propagaCancellation() = runTest {
        transport.enqueue { awaitCancellation() }
        val call = async { client.execute(get) }
        advanceUntilIdle()

        call.cancel()

        try {
            call.await()
            fail("debía propagar la cancelación")
        } catch (_: CancellationException) {
            assertTrue(call.isCancelled)
        }
    }

    @Test
    fun tokenYCuerpoNoAparecenEnMensajesDeError() = runTest {
        session.start("token-secreto", "r0")
        transport.enqueue(failure(TransportFailureKind.TIMEOUT))
        transport.enqueue(response(500, "stack con token-secreto y pwd-secreto"))
        val request = ApiRequest(HttpMethod.POST, "x", jsonBody = "{\"password\":\"pwd-secreto\"}")

        val results = listOf(client.execute(request), client.execute(request)).map(::failure)

        results.forEach { result ->
            assertFalse(result.error.message.contains("secreto"))
            assertFalse(result.error.code.contains("secreto"))
            assertFalse(result.toString().contains("pwd-secreto"))
        }
        assertFalse(request.toString().contains("pwd-secreto"))
    }

    @Test
    fun untrustedOrigin403_devuelveForbiddenMobileClientNotSupported_sinRenovarNiLimpiarSesion() = runTest {
        session.start("a0", "r0")
        transport.enqueue(response(403, errorBody("UNTRUSTED_ORIGIN")))

        val result = failure(client.execute(get))

        assertEquals(DomainError.Forbidden::class, result.error::class)
        assertEquals("MOBILE_CLIENT_NOT_SUPPORTED", result.error.code)
        assertTrue(refresher.receivedTokens.isEmpty())
        assertEquals("r0", store.value)
        assertEquals("a0", session.currentAccessToken())
    }

    @Test
    fun untrustedOrigin403_noEmiteEnExpirations() = runTest {
        session.start("a0", "r0")
        val expirations = countExpirations(session)
        transport.enqueue(response(403, errorBody("UNTRUSTED_ORIGIN")))

        client.execute(get)

        assertEquals(0, expirations.value)
    }

    @Test
    fun refreshConTimeout_devuelveNetworkYElRefreshTokenGuardadoSigueIgual() = runTest {
        val backend = FakeRotatingBackend(grace = true).also { it.seed("a0", "r0") }
        val realSession = AuthSession(store, HttpSessionRefresher(backend, TEST_CONFIG))
        realSession.start("a0", "r0")
        backend.expireAccess("a0")
        backend.dropNextRefreshResponse = true

        val result = failure(RealApiClient(backend, realSession, TEST_CONFIG).execute(get))

        assertEquals("NETWORK_TIMEOUT", result.error.code)
        assertEquals("r0", store.value)
    }

    @Test
    fun refreshAmbiguo_noReintentaLaPeticionOriginal() = runTest {
        session.start("a0", "r0")
        transport.enqueue(unauthenticated)
        refresher.enqueue(RefreshOutcome.Unavailable(DomainError.Network("NETWORK_TIMEOUT", "x")))

        client.execute(get)

        assertEquals(1, transport.requests.size)
    }

    @Test
    fun refreshPerdidoLuegoReintentoConGracia_laSegundaPeticionTieneExito() = runTest {
        val backend = FakeRotatingBackend(grace = true).also { it.seed("a0", "r0") }
        val realSession = AuthSession(store, HttpSessionRefresher(backend, TEST_CONFIG))
        val realClient = RealApiClient(backend, realSession, TEST_CONFIG)
        realSession.start("a0", "r0")
        backend.expireAccess("a0")
        backend.dropNextRefreshResponse = true

        val first = realClient.execute(get)
        val second = realClient.execute(get)

        assertTrue(first is ApiResult.Failure)
        assertTrue(second is ApiResult.Success)
        assertEquals("refresh-1", store.value)
    }

    @Test
    fun refreshPerdidoLuegoReintentoSinGracia_devuelveUnauthenticatedYEmiteUnaVez() = runTest {
        val backend = FakeRotatingBackend(grace = false).also { it.seed("a0", "r0") }
        val realSession = AuthSession(store, HttpSessionRefresher(backend, TEST_CONFIG))
        val realClient = RealApiClient(backend, realSession, TEST_CONFIG)
        val expirations = countExpirations(realSession)
        realSession.start("a0", "r0")
        backend.expireAccess("a0")
        backend.dropNextRefreshResponse = true

        realClient.execute(get)
        val second = failure(realClient.execute(get))

        assertTrue(second.error is DomainError.Unauthenticated)
        assertEquals(1, expirations.value)
        assertNull(store.value)
    }
}
