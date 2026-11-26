package com.simposio.taskeer.data.remote.session

import com.simposio.taskeer.data.remote.FakeHttpTransport
import com.simposio.taskeer.data.remote.FakeRotatingBackend
import com.simposio.taskeer.data.remote.FakeSessionRefresher
import com.simposio.taskeer.data.remote.InMemoryRefreshTokenStore
import com.simposio.taskeer.data.remote.TEST_CONFIG
import com.simposio.taskeer.data.remote.response
import com.simposio.taskeer.domain.model.DomainError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthSessionTest {

    private val store = InMemoryRefreshTokenStore("r0")
    private val refresher = FakeSessionRefresher()
    private val session = AuthSession(store, refresher)

    private val network = DomainError.Network("NETWORK_TIMEOUT", "Tiempo agotado")
    private val unauthenticated = DomainError.Unauthenticated("INVALID_REFRESH_TOKEN", "Vencido")

    private class Counter(var value: Int = 0)

    private fun TestScope.countExpirations(target: AuthSession): Counter {
        val counter = Counter()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            target.expirations.collect { counter.value++ }
        }
        return counter
    }

    @Test
    fun renew_conRefreshValido_persisteElNuevoTokenAntesDeDevolver() = runTest {
        refresher.enqueue(RefreshOutcome.Renewed("a1", "r1"))

        val outcome = session.renew(null)

        assertEquals(RenewOutcome.Renewed("a1"), outcome)
        assertEquals("r1", store.value)
        assertEquals("a1", session.currentAccessToken())
    }

    @Test
    fun cincoRenovacionesConcurrentes_haceUnSoloRefresh() = runTest {
        session.start("a0", "r0")
        val gate = CompletableDeferred<Unit>().also { refresher.gate = it }
        refresher.enqueue(RefreshOutcome.Renewed("a1", "r1"))
        val calls = (1..5).map { async { session.renew("a0") } }
        advanceUntilIdle()

        gate.complete(Unit)
        val results = calls.awaitAll()

        assertEquals(1, refresher.receivedTokens.size)
        assertEquals(List(5) { RenewOutcome.Renewed("a1") }, results)
    }

    @Test
    fun renew_conTokenDistintoDelVigente_noHaceRefresh() = runTest {
        session.start("a1", "r1")

        val outcome = session.renew("a0")

        assertEquals(RenewOutcome.Renewed("a1"), outcome)
        assertTrue(refresher.receivedTokens.isEmpty())
    }

    @Test
    fun renew_sinRefreshToken_devuelveSessionExpired() = runTest {
        store.value = null

        assertEquals(RenewOutcome.SessionExpired, session.renew(null))
        assertTrue(refresher.receivedTokens.isEmpty())
    }

    @Test
    fun renew_rechazado_borraTokensYEmiteExpiracion() = runTest {
        session.start("a0", "r0")
        val expirations = countExpirations(session)
        refresher.enqueue(RefreshOutcome.Rejected(unauthenticated))

        val outcome = session.renew("a0")

        assertEquals(RenewOutcome.SessionExpired, outcome)
        assertNull(store.value)
        assertNull(session.currentAccessToken())
        assertEquals(1, expirations.value)
    }

    @Test
    fun renew_sinRed_conservaRefreshTokenYNoEmite() = runTest {
        session.start("a0", "r0")
        val expirations = countExpirations(session)
        refresher.enqueue(RefreshOutcome.Unavailable(network))

        val outcome = session.renew("a0")

        assertEquals(RenewOutcome.Failed(network, null), outcome)
        assertEquals("r0", store.value)
        assertEquals(0, expirations.value)
    }

    @Test
    fun renew_con429_conservaTodoYDevuelveRetryAfter() = runTest {
        session.start("a0", "r0")
        val limited = DomainError.RateLimited("TOO_MANY_REQUESTS", "Espera")
        refresher.enqueue(RefreshOutcome.Unavailable(limited, retryAfterSeconds = 30))

        val outcome = session.renew("a0")

        assertEquals(RenewOutcome.Failed(limited, 30), outcome)
        assertEquals("r0", store.value)
        assertEquals("a0", session.currentAccessToken())
    }

    @Test
    fun renew_null_haceRefreshSilencioso() = runTest {
        refresher.enqueue(RefreshOutcome.Renewed("a1", "r1"))

        session.renew(null)

        assertEquals(listOf("r0"), refresher.receivedTokens)
    }

    @Test
    fun renew_null_conAccessTokenPresente_devuelveEse() = runTest {
        session.start("a0", "r0")

        assertEquals(RenewOutcome.Renewed("a0"), session.renew(null))
        assertTrue(refresher.receivedTokens.isEmpty())
    }

    @Test
    fun renew_cancelacion_liberaElBloqueoSinPerderElToken() = runTest {
        refresher.enqueue { awaitCancellation() }
        val first = launch { session.renew(null) }
        advanceUntilIdle()

        first.cancel()
        advanceUntilIdle()
        refresher.enqueue(RefreshOutcome.Renewed("a1", "r1"))
        val outcome = session.renew(null)

        assertEquals(RenewOutcome.Renewed("a1"), outcome)
        assertEquals(listOf("r0", "r0"), refresher.receivedTokens)
    }

    @Test
    fun start_guardaAccessEnMemoriaYRefreshEnElAlmacen() = runTest {
        store.value = null

        session.start("a0", "r9")

        assertEquals("a0", session.currentAccessToken())
        assertEquals("r9", store.value)
        assertTrue(session.hasStoredSession())
    }

    @Test
    fun start_limpiaElTokenQueQuedoSoloEnMemoria() = runTest {
        store.failOnWrite = true
        session.start("a0", "r-memoria")
        store.failOnWrite = false
        session.start("a1", "r-nuevo")
        refresher.enqueue(RefreshOutcome.Renewed("a2", "r2"))

        session.renew("a1")

        assertEquals(listOf("r-nuevo"), refresher.receivedTokens)
    }

    @Test
    fun clear_borraSinEmitirExpiracion() = runTest {
        session.start("a0", "r0")
        val expirations = countExpirations(session)

        session.clear()

        assertNull(session.currentAccessToken())
        assertNull(store.value)
        assertEquals(0, expirations.value)
    }

    @Test
    fun renew_siFallaLaEscrituraDelToken_borraElViejoYConservaElNuevoSoloEnMemoria() = runTest {
        session.start("a0", "r0")
        store.failOnWrite = true
        refresher.enqueue(RefreshOutcome.Renewed("a1", "r1"))
        refresher.enqueue(RefreshOutcome.Renewed("a2", "r2"))

        val outcome = session.renew("a0")
        session.renew("a1")

        assertEquals(RenewOutcome.Renewed("a1"), outcome)
        assertNull(store.value)
        assertEquals(listOf("r0", "r1"), refresher.receivedTokens)
    }

    @Test
    fun renew_conTimeout_noBorraNiSustituyeElRefreshTokenGuardado() = runTest {
        session.start("a0", "r0")
        refresher.enqueue(RefreshOutcome.Unavailable(network))

        session.renew("a0")

        assertEquals("r0", store.value)
        assertEquals("a0", session.currentAccessToken())
    }

    @Test
    fun renew_conCorteDeConexion_noCierraSesionNiEmite() = runTest {
        session.start("a0", "r0")
        val expirations = countExpirations(session)
        refresher.enqueue(RefreshOutcome.Unavailable(DomainError.Network("NETWORK_IO", "Corte")))

        val outcome = session.renew("a0")

        assertTrue(outcome is RenewOutcome.Failed)
        assertEquals("r0", store.value)
        assertEquals(0, expirations.value)
    }

    @Test
    fun renew_con5xx_conservaTokenYSesion() = runTest {
        session.start("a0", "r0")
        refresher.enqueue(RefreshOutcome.Unavailable(DomainError.Server("INTERNAL_SERVER_ERROR", "Error")))

        session.renew("a0")

        assertEquals("r0", store.value)
        assertEquals("a0", session.currentAccessToken())
    }

    @Test
    fun renew_con429_noTocaElAlmacen() = runTest {
        session.start("a0", "r0")
        val writesBefore = store.writes
        refresher.enqueue(RefreshOutcome.Unavailable(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), 5))

        session.renew("a0")

        assertEquals(writesBefore, store.writes)
        assertEquals("r0", store.value)
    }

    @Test
    fun renew_con2xxSinTokens_esFalloAmbiguoYNoTocaElAlmacen() = runTest {
        val transport = FakeHttpTransport().apply { enqueue(response(200, "{\"accessToken\":\"a1\"}")) }
        val realSession = AuthSession(store, HttpSessionRefresher(transport, TEST_CONFIG))
        realSession.start("a0", "r0")

        val outcome = realSession.renew("a0")

        assertTrue(outcome is RenewOutcome.Failed)
        assertEquals("r0", store.value)
    }

    @Test
    fun renew_reintentoPosterior_usaElMismoRefreshTokenGuardado() = runTest {
        session.start("a0", "r0")
        refresher.enqueue(RefreshOutcome.Unavailable(network))
        refresher.enqueue(RefreshOutcome.Renewed("a1", "r1"))

        session.renew("a0")
        val outcome = session.renew("a0")

        assertEquals(RenewOutcome.Renewed("a1"), outcome)
        assertEquals(listOf("r0", "r0"), refresher.receivedTokens)
    }

    @Test
    fun renew_nuncaUsaUnTokenDerivado() = runTest {
        session.start("a0", "r0")
        repeat(3) { refresher.enqueue(RefreshOutcome.Unavailable(network)) }

        repeat(3) { session.renew("a0") }

        assertEquals(setOf("r0"), refresher.receivedTokens.toSet())
    }

    @Test
    fun cincoRenovacionesConcurrentesConFalloDeRed_haceUnSoloRefreshYTodasRecibenFailed() = runTest {
        session.start("a0", "r0")
        val gate = CompletableDeferred<Unit>().also { refresher.gate = it }
        refresher.enqueue(RefreshOutcome.Unavailable(network))
        val calls = (1..5).map { async { session.renew("a0") } }
        advanceUntilIdle()

        gate.complete(Unit)
        val results = calls.awaitAll()

        assertEquals(1, refresher.receivedTokens.size)
        assertEquals(List(5) { RenewOutcome.Failed(network, null) }, results)
    }

    @Test
    fun renew_llamadorPosteriorAlFallo_haceUnIntentoNuevo() = runTest {
        session.start("a0", "r0")
        refresher.enqueue(RefreshOutcome.Unavailable(network))
        refresher.enqueue(RefreshOutcome.Unavailable(network))

        session.renew("a0")
        session.renew("a0")

        assertEquals(2, refresher.receivedTokens.size)
    }

    @Test
    fun renew_noReintentaInternamente() = runTest {
        session.start("a0", "r0")
        refresher.enqueue(RefreshOutcome.Unavailable(network))

        session.renew("a0")

        assertEquals(1, refresher.receivedTokens.size)
    }

    @Test
    fun renew_elAlmacenYaTieneElTokenNuevoCuandoSePublicaElAccessToken() = runTest {
        session.start("a0", "r0")
        var accessSeenDuringWrite: String? = "no-observado"
        store.onWrite = { accessSeenDuringWrite = session.currentAccessToken() }
        refresher.enqueue(RefreshOutcome.Renewed("a1", "r1"))

        session.renew("a0")

        assertEquals("a0", accessSeenDuringWrite)
        assertEquals("r1", store.value)
        assertEquals("a1", session.currentAccessToken())
    }

    @Test
    fun renew_elAlmacenYaTieneElTokenNuevoAntesDeSoltarElBloqueo() = runTest {
        session.start("a0", "r0")
        val gate = CompletableDeferred<Unit>().also { refresher.gate = it }
        refresher.enqueue(RefreshOutcome.Renewed("a1", "r1"))
        var storedWhenSecondReturned: String? = null
        val first = async { session.renew("a0") }
        val second = async {
            session.renew("a0").also { storedWhenSecondReturned = store.value }
        }
        advanceUntilIdle()

        gate.complete(Unit)

        assertEquals(RenewOutcome.Renewed("a1"), first.await())
        assertEquals(RenewOutcome.Renewed("a1"), second.await())
        assertEquals("r1", storedWhenSecondReturned)
        assertEquals(1, refresher.receivedTokens.size)
    }

    @Test
    fun renew_con401TokenReutilizado_borraTodoYEmiteUnaVez() = runTest {
        session.start("a0", "r0")
        val expirations = countExpirations(session)
        refresher.enqueue(RefreshOutcome.Rejected(DomainError.Unauthenticated("REFRESH_TOKEN_REUSED", "x")))

        session.renew("a0")
        session.renew("a0")

        assertNull(store.value)
        assertNull(session.currentAccessToken())
        assertEquals(1, expirations.value)
    }

    @Test
    fun renew_con401SinCodigoReconocible_noCierraSesion() = runTest {
        val transport = FakeHttpTransport().apply { enqueue(response(401, "<html>proxy</html>")) }
        val realSession = AuthSession(store, HttpSessionRefresher(transport, TEST_CONFIG))
        realSession.start("a0", "r0")
        val expirations = countExpirations(realSession)

        val outcome = realSession.renew("a0")

        assertTrue(outcome is RenewOutcome.Failed)
        assertEquals("r0", store.value)
        assertEquals(0, expirations.value)
    }

    @Test
    fun renew_trasCerrarSesion_noVuelveAUsarElTokenAntiguo() = runTest {
        session.start("a0", "r0")
        refresher.enqueue(RefreshOutcome.Rejected(unauthenticated))

        session.renew("a0")
        val second = session.renew("a0")

        assertEquals(RenewOutcome.SessionExpired, second)
        assertEquals(listOf("r0"), refresher.receivedTokens)
    }

    @Test
    fun respuestaPerdidaTrasRotar_reintentoConMismoToken_recibeElMismoParConGracia() = runTest {
        val backend = FakeRotatingBackend(grace = true).also { it.seed("a0", "r0") }
        val realSession = AuthSession(store, HttpSessionRefresher(backend, TEST_CONFIG))
        realSession.start("a0", "r0")
        backend.dropNextRefreshResponse = true

        val lost = realSession.renew("a0")
        val retried = realSession.renew("a0")

        assertTrue(lost is RenewOutcome.Failed)
        assertEquals(RenewOutcome.Renewed("access-1"), retried)
        assertEquals("refresh-1", store.value)
    }

    @Test
    fun respuestaPerdidaTrasRotar_sinGracia_cierraSesionYEmite() = runTest {
        val backend = FakeRotatingBackend(grace = false).also { it.seed("a0", "r0") }
        val realSession = AuthSession(store, HttpSessionRefresher(backend, TEST_CONFIG))
        realSession.start("a0", "r0")
        val expirations = countExpirations(realSession)
        backend.dropNextRefreshResponse = true

        realSession.renew("a0")
        val retried = realSession.renew("a0")

        assertEquals(RenewOutcome.SessionExpired, retried)
        assertNull(store.value)
        assertEquals(1, expirations.value)
        assertTrue(backend.revoked)
    }
}
