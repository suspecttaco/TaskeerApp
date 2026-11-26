package com.simposio.taskeer.data.remote.auth

import com.simposio.taskeer.data.remote.ApiErrorMapper
import com.simposio.taskeer.data.remote.FakeHttpTransport
import com.simposio.taskeer.data.remote.FakeSessionRefresher
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.remote.InMemoryRefreshTokenStore
import com.simposio.taskeer.data.remote.RealApiClient
import com.simposio.taskeer.data.remote.TEST_CONFIG
import com.simposio.taskeer.data.remote.TransportFailureKind
import com.simposio.taskeer.data.remote.errorBody
import com.simposio.taskeer.data.remote.failure
import com.simposio.taskeer.data.remote.response
import com.simposio.taskeer.data.remote.session.AuthSession
import com.simposio.taskeer.data.remote.session.HttpSessionRefresher
import com.simposio.taskeer.data.remote.session.RefreshOutcome
import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.LoginOutcome
import com.simposio.taskeer.domain.auth.RegisterOutcome
import com.simposio.taskeer.domain.auth.ResendVerificationOutcome
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.auth.VerifyEmailOutcome
import com.simposio.taskeer.domain.model.DomainError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteAuthRepositoryTest {

    private val transport = FakeHttpTransport()
    private val store = InMemoryRefreshTokenStore()
    private val refresher = FakeSessionRefresher()
    private val session = AuthSession(store, refresher)
    private val client = RealApiClient(transport, session, TEST_CONFIG)

    private val account = AccountSummary("ana@example.com", "Ana")
    private val network = DomainError.Network("NETWORK_TIMEOUT", "Tiempo agotado")

    private fun TestScope.repository(
        authSession: AuthSession = session,
        apiClient: RealApiClient = client,
    ) = RemoteAuthRepository(apiClient, authSession, backgroundScope + UnconfinedTestDispatcher(testScheduler))

    private fun loginJson(refresh: String? = "r1", access: String = "a1"): String {
        val json = JSONObject().put("accessToken", access)
            .put("user", JSONObject().put("email", "ana@example.com").put("displayName", "Ana"))
        if (refresh != null) json.put("refreshToken", refresh)
        return json.toString()
    }

    private fun password(value: String = "secreta") = value.toCharArray()

    private suspend fun RemoteAuthRepository.signIn() {
        transport.enqueue(response(200, loginJson()))
        login("ana@example.com", password())
    }

    private fun failed(outcome: LoginOutcome): LoginOutcome.Failed = outcome as LoginOutcome.Failed

    @Test
    fun login_exitoso_haceLaPeticionCorrectaSinAuthorization() = runTest {
        transport.enqueue(response(200, loginJson()))

        repository().login("  ana@example.com ", password())

        val request = transport.requests.single()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("https://api.test/api/v1/auth/login", request.url)
        assertNull(request.headers["Authorization"])
        val body = JSONObject(requireNotNull(request.body))
        assertEquals("ana@example.com", body.getString("email"))
        assertEquals("secreta", body.getString("password"))
    }

    @Test
    fun login_exitoso_iniciaSesionConLosTokensDeLaRespuesta() = runTest {
        transport.enqueue(response(200, loginJson(refresh = "r1", access = "a1")))

        repository().login("ana@example.com", password())

        assertEquals("a1", session.currentAccessToken())
        assertEquals("r1", store.value)
    }

    @Test
    fun login_exitoso_publicaSignedInConElCorreo() = runTest {
        transport.enqueue(response(200, loginJson()))
        val repository = repository()

        val outcome = repository.login("ana@example.com", password())

        assertEquals(LoginOutcome.SignedIn(account), outcome)
        assertEquals(SessionState.SignedIn(account, offline = false), repository.sessionState.value)
    }

    @Test
    fun login_exitoso_enviaXClientMobile() = runTest {
        transport.enqueue(response(200, loginJson()))

        repository().login("ana@example.com", password())

        assertEquals("mobile", transport.requests.single().headers["X-Client"])
    }

    @Test
    fun login_conCredencialesInvalidas_noHacePeticion() = runTest {
        val repository = repository()

        val outcome = failed(repository.login("sin-arroba", password()))

        assertEquals("EMAIL_INVALID", outcome.error.code)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun login_con401InvalidCredentials_esUnauthenticatedYNoIniciaSesion() = runTest {
        transport.enqueue(response(401, errorBody("INVALID_CREDENTIALS")))
        val repository = repository()

        val outcome = failed(repository.login("ana@example.com", password()))

        assertTrue(outcome.error is DomainError.Unauthenticated)
        assertEquals("INVALID_CREDENTIALS", outcome.error.code)
        assertFalse(session.hasStoredSession())
        assertEquals(SessionState.SignedOut(), repository.sessionState.value)
    }

    @Test
    fun login_con401_noDisparaRenovacion() = runTest {
        transport.enqueue(response(401, errorBody("UNAUTHENTICATED")))

        repository().login("ana@example.com", password())

        assertEquals(1, transport.requests.size)
        assertTrue(refresher.receivedTokens.isEmpty())
    }

    @Test
    fun login_con429_devuelveRateLimitedConRetryAfter() = runTest {
        transport.enqueue(response(429, errorBody("TOO_MANY_REQUESTS"), mapOf("retry-after" to "120")))

        val outcome = failed(repository().login("ana@example.com", password()))

        assertTrue(outcome.error is DomainError.RateLimited)
        assertEquals(120L, outcome.retryAfterSeconds)
    }

    @Test
    fun login_sinRed_devuelveNetworkYNoIniciaSesion() = runTest {
        transport.enqueue(failure(TransportFailureKind.UNREACHABLE))

        val outcome = failed(repository().login("ana@example.com", password()))

        assertTrue(outcome.error is DomainError.Network)
        assertFalse(session.hasStoredSession())
    }

    @Test
    fun login_con500_devuelveServer() = runTest {
        transport.enqueue(response(500, ""))

        val outcome = failed(repository().login("ana@example.com", password()))

        assertTrue(outcome.error is DomainError.Server)
    }

    @Test
    fun login_con400_devuelveValidation() = runTest {
        transport.enqueue(response(400, errorBody("VALIDATION_ERROR")))

        val outcome = failed(repository().login("ana@example.com", password()))

        assertTrue(outcome.error is DomainError.Validation)
    }

    @Test
    fun login_sinRefreshToken_devuelveMobileClientNotSupportedYNoIniciaSesion() = runTest {
        transport.enqueue(response(200, loginJson(refresh = null)))
        val repository = repository()

        val outcome = failed(repository.login("ana@example.com", password()))

        assertEquals(
            DomainError.Forbidden("MOBILE_CLIENT_NOT_SUPPORTED", ApiErrorMapper.MOBILE_CLIENT_MESSAGE),
            outcome.error,
        )
        assertNull(session.currentAccessToken())
        assertFalse(session.hasStoredSession())
        assertEquals(SessionState.SignedOut(), repository.sessionState.value)
    }

    @Test
    fun login_sinRefreshToken_noEscribeNadaEnElAlmacen() = runTest {
        transport.enqueue(response(200, loginJson(refresh = null)))

        repository().login("ana@example.com", password())

        assertEquals(0, store.writes)
        assertNull(store.value)
    }

    @Test
    fun login_cuerpoIlegible_devuelveInvalidLoginResponse() = runTest {
        transport.enqueue(response(200, "no es json"))
        transport.enqueue(response(200, "{}"))
        val repository = repository()

        val unreadable = failed(repository.login("ana@example.com", password()))
        val withoutToken = failed(repository.login("ana@example.com", password()))

        assertEquals(DomainError.Server("INVALID_LOGIN_RESPONSE", unreadable.error.message), unreadable.error)
        assertEquals("INVALID_LOGIN_RESPONSE", withoutToken.error.code)
        assertFalse(session.hasStoredSession())
    }

    @Test
    fun login_con403UntrustedOrigin_devuelveMobileClientNotSupported() = runTest {
        transport.enqueue(response(403, errorBody("UNTRUSTED_ORIGIN")))

        val outcome = failed(repository().login("ana@example.com", password()))

        assertTrue(outcome.error is DomainError.Forbidden)
        assertEquals("MOBILE_CLIENT_NOT_SUPPORTED", outcome.error.code)
    }

    @Test
    fun login_conSesionActiva_noHacePeticion() = runTest {
        val repository = repository()
        repository.signIn()

        val outcome = failed(repository.login("ana@example.com", password()))

        assertEquals("ALREADY_SIGNED_IN", outcome.error.code)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun login_vaciaLaPasswordTrasExito() = runTest {
        transport.enqueue(response(200, loginJson()))
        val secret = password()

        repository().login("ana@example.com", secret)

        assertTrue(secret.all { it == '\u0000' })
    }

    @Test
    fun login_vaciaLaPasswordTrasCadaFallo() = runTest {
        val responses = listOf(
            response(401, errorBody("INVALID_CREDENTIALS")),
            response(429, errorBody("TOO_MANY_REQUESTS")),
            failure(TransportFailureKind.TIMEOUT),
            response(500, ""),
            response(200, loginJson(refresh = null)),
            response(200, "ilegible"),
        )
        val repository = repository()

        responses.forEach { result ->
            transport.enqueue(result)
            val secret = password()
            repository.login("ana@example.com", secret)
            assertTrue(secret.all { it == '\u0000' })
        }
    }

    @Test
    fun login_vaciaLaPasswordSiLaValidacionFalla() = runTest {
        val secret = password()

        repository().login("sin-arroba", secret)

        assertTrue(secret.all { it == '\u0000' })
    }

    @Test
    fun login_canceladoTrasLaRespuesta_terminaDeIniciarLaSesion() = runTest {
        val repository = repository()
        lateinit var job: Job
        transport.enqueue { _ ->
            job.cancel()
            response(200, loginJson())
        }
        job = launch { repository.login("ana@example.com", password()) }

        advanceUntilIdle()
        job.join()

        assertEquals("r1", store.value)
        assertEquals("a1", session.currentAccessToken())
        assertEquals(SessionState.SignedIn(account, offline = false), repository.sessionState.value)
    }

    @Test
    fun resume_sinSesionGuardada_noHacePeticiones() = runTest {
        repository().resume()

        assertTrue(transport.requests.isEmpty())
        assertTrue(refresher.receivedTokens.isEmpty())
    }

    @Test
    fun resume_sinSesionGuardada_quedaSignedOut() = runTest {
        val repository = repository()

        repository.resume()

        assertEquals(SessionState.SignedOut(expired = false), repository.sessionState.value)
    }

    @Test
    fun resume_conSesion_renuevaYQuedaSignedIn() = runTest {
        store.value = "r0"
        refresher.enqueue(RefreshOutcome.Renewed("a1", "r1"))
        val repository = repository()

        repository.resume()

        assertEquals(SessionState.SignedIn(account = null, offline = false), repository.sessionState.value)
        assertEquals("r1", store.value)
    }

    @Test
    fun resume_rechazado_quedaSignedOutExpirado() = runTest {
        store.value = "r0"
        refresher.enqueue(RefreshOutcome.Rejected(DomainError.Unauthenticated("INVALID_REFRESH_TOKEN", "vencido")))
        val repository = repository()

        repository.resume()

        assertEquals(SessionState.SignedOut(expired = true), repository.sessionState.value)
        assertNull(store.value)
    }

    @Test
    fun resume_sinRed_conservaSesionYQuedaOffline() = runTest {
        store.value = "r0"
        refresher.enqueue(RefreshOutcome.Unavailable(network))
        val repository = repository()

        repository.resume()

        assertEquals(SessionState.SignedIn(account = null, offline = true), repository.sessionState.value)
        assertEquals("r0", store.value)
    }

    @Test
    fun resume_con429_conservaSesionYQuedaOffline() = runTest {
        store.value = "r0"
        refresher.enqueue(
            RefreshOutcome.Unavailable(DomainError.RateLimited("TOO_MANY_REQUESTS", "limite"), retryAfterSeconds = 30),
        )
        val repository = repository()

        repository.resume()

        assertEquals(SessionState.SignedIn(account = null, offline = true), repository.sessionState.value)
        assertEquals("r0", store.value)
    }

    @Test
    fun resume_conMobileNotSupported_noCierraSesionNiBorraElToken() = runTest {
        store.value = "r0"
        refresher.enqueue(
            RefreshOutcome.Unavailable(DomainError.Forbidden("MOBILE_CLIENT_NOT_SUPPORTED", "no compatible")),
        )
        val repository = repository()

        repository.resume()

        assertEquals(SessionState.SignedIn(account = null, offline = true), repository.sessionState.value)
        assertEquals("r0", store.value)
    }

    @Test
    fun resume_esIdempotenteConSesionActiva() = runTest {
        store.value = "r0"
        refresher.enqueue(RefreshOutcome.Renewed("a1", "r1"))
        val repository = repository()

        repository.resume()
        repository.resume()

        assertEquals(1, refresher.receivedTokens.size)
    }

    @Test
    fun resume_tras_logoutConcurrente_noResucitaLaSesion() = runTest {
        store.value = "r0"
        val gate = CompletableDeferred<Unit>()
        refresher.enqueue { _ ->
            withContext(NonCancellable) { gate.await() }
            RefreshOutcome.Renewed("a1", "r1")
        }
        transport.enqueue(response(200, "{}"))
        val repository = repository()
        val resuming = launch { repository.resume() }
        advanceUntilIdle()
        val loggingOut = async { repository.logout() }
        advanceUntilIdle()

        gate.complete(Unit)
        loggingOut.await()
        resuming.join()

        assertEquals(SessionState.SignedOut(expired = false), repository.sessionState.value)
        assertFalse(session.hasStoredSession())
    }

    @Test
    fun resume_reintentadoTrasFalloDeRed_recuperaLaSesion() = runTest {
        store.value = "r0"
        refresher.enqueue(RefreshOutcome.Unavailable(network))
        refresher.enqueue(RefreshOutcome.Renewed("a1", "r1"))
        val repository = repository()

        repository.resume()
        repository.resume()

        assertEquals(SessionState.SignedIn(account = null, offline = false), repository.sessionState.value)
        assertEquals("r1", store.value)
    }

    @Test
    fun expiracion_deAuthSession_pasaASignedOutExpirado() = runTest {
        val repository = repository()
        repository.signIn()
        refresher.enqueue(RefreshOutcome.Rejected(DomainError.Unauthenticated("INVALID_REFRESH_TOKEN", "vencido")))

        session.renew("a1")

        assertEquals(SessionState.SignedOut(expired = true), repository.sessionState.value)
    }

    @Test
    fun expiracion_emiteUnaSolaVezAunqueLleguenDosVias() = runTest {
        store.value = "r0"
        refresher.enqueue(RefreshOutcome.Rejected(DomainError.Unauthenticated("INVALID_REFRESH_TOKEN", "vencido")))
        val repository = repository()
        val seen = mutableListOf<SessionState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.sessionState.collect { seen += it } }

        repository.resume()

        assertEquals(
            listOf(SessionState.SignedOut(), SessionState.Resuming, SessionState.SignedOut(expired = true)),
            seen,
        )
    }

    @Test
    fun expiracion_trasSignedOut_noCambiaNada() = runTest {
        val repository = repository()
        session.start("a0", "r0")
        refresher.enqueue(RefreshOutcome.Rejected(DomainError.Unauthenticated("INVALID_REFRESH_TOKEN", "vencido")))

        session.renew("a0")

        assertEquals(SessionState.SignedOut(expired = false), repository.sessionState.value)
    }

    @Test
    fun acknowledgeExpiration_limpiaElAviso() = runTest {
        store.value = "r0"
        refresher.enqueue(RefreshOutcome.Rejected(DomainError.Unauthenticated("INVALID_REFRESH_TOKEN", "vencido")))
        val repository = repository()
        repository.resume()

        repository.acknowledgeExpiration()

        assertEquals(SessionState.SignedOut(expired = false), repository.sessionState.value)
    }

    @Test
    fun logoutVoluntario_noMarcaExpirada() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(200, "{}"))

        repository.logout()
        advanceUntilIdle()

        assertEquals(SessionState.SignedOut(expired = false), repository.sessionState.value)
    }

    @Test
    fun colectorSuscritoAntesDeCualquierRenew() = runTest {
        val repository = RemoteAuthRepository(client, session, backgroundScope)
        repository.signIn()
        refresher.enqueue(RefreshOutcome.Rejected(DomainError.Unauthenticated("INVALID_REFRESH_TOKEN", "vencido")))

        session.renew("a1")
        runCurrent()

        assertEquals(SessionState.SignedOut(expired = true), repository.sessionState.value)
    }

    @Test
    fun logout_conSesion_revocaConElRefreshEnElCuerpoSinAuthorization() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(200, "{}"))

        repository.logout()

        val request = transport.requests.last()
        assertEquals("https://api.test/api/v1/auth/logout", request.url)
        assertEquals(HttpMethod.POST, request.method)
        assertNull(request.headers["Authorization"])
        assertEquals("r1", JSONObject(requireNotNull(request.body)).getString("refreshToken"))
    }

    @Test
    fun logout_limpiaLaSesionLocalYPublicaSignedOut() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(200, "{}"))

        repository.logout()

        assertNull(store.value)
        assertNull(session.currentAccessToken())
        assertEquals(SessionState.SignedOut(), repository.sessionState.value)
    }

    @Test
    fun logout_con2xx_serverRevokedTrue() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(204, ""))

        assertTrue(repository.logout().serverRevoked)
    }

    @Test
    fun logout_con401_serverRevokedTrue() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(401, errorBody("INVALID_REFRESH_TOKEN")))

        assertTrue(repository.logout().serverRevoked)
    }

    @Test
    fun logout_sinRed_serverRevokedFalseYSesionLimpia() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(failure(TransportFailureKind.UNREACHABLE))

        assertFalse(repository.logout().serverRevoked)
        assertNull(store.value)
        assertEquals(SessionState.SignedOut(), repository.sessionState.value)
    }

    @Test
    fun logout_con429_serverRevokedFalse() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(429, errorBody("TOO_MANY_REQUESTS")))

        assertFalse(repository.logout().serverRevoked)
    }

    @Test
    fun logout_con500_serverRevokedFalse() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(500, ""))

        assertFalse(repository.logout().serverRevoked)
    }

    @Test
    fun logout_con403UntrustedOrigin_serverRevokedFalse() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(403, errorBody("UNTRUSTED_ORIGIN")))

        assertFalse(repository.logout().serverRevoked)
    }

    @Test
    fun logout_sinSesion_noHacePeticionYSeConsideraRevocado() = runTest {
        val outcome = repository().logout()

        assertTrue(outcome.serverRevoked)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun logout_noReintentaEnSegundoPlano() = runTest {
        session.start("a0", "r0")
        transport.enqueue(failure(TransportFailureKind.TIMEOUT))
        val repository = repository()

        repository.logout()
        advanceUntilIdle()

        assertEquals(1, transport.requests.size)
    }

    @Test
    fun logout_noBorraDatosLocales() {
        val parameterTypes = RemoteAuthRepository::class.java.constructors.single().parameterTypes.map { it.name }

        assertTrue(parameterTypes.none { it.contains("data.local") || it.endsWith("Dao") })
    }

    @Test
    fun logout_durante_resume_dejaSignedOut() = runTest {
        store.value = "r0"
        val gate = CompletableDeferred<Unit>().also { refresher.gate = it }
        refresher.enqueue(RefreshOutcome.Renewed("a1", "r1"))
        transport.enqueue(response(200, "{}"))
        val repository = repository()
        val resuming = launch { repository.resume() }
        advanceUntilIdle()
        assertEquals(SessionState.Resuming, repository.sessionState.value)

        repository.logout()
        advanceUntilIdle()
        resuming.join()

        assertEquals(SessionState.SignedOut(expired = false), repository.sessionState.value)
        assertFalse(gate.isCompleted)
        assertNull(store.value)
        assertEquals("r0", JSONObject(requireNotNull(transport.requests.last().body)).getString("refreshToken"))
    }

    @Test
    fun logout_conTokenRotadoEnVuelo_revocaElNuevo() = runTest {
        session.start("a0", "r0")
        val gate = CompletableDeferred<Unit>().also { refresher.gate = it }
        refresher.enqueue(RefreshOutcome.Renewed("a1", "r1"))
        transport.enqueue(response(200, "{}"))
        val repository = repository()
        val renewing = launch { session.renew("a0") }
        advanceUntilIdle()
        val loggingOut = async { repository.logout() }
        advanceUntilIdle()

        gate.complete(Unit)
        renewing.join()

        assertTrue(loggingOut.await().serverRevoked)
        assertEquals("r1", JSONObject(requireNotNull(transport.requests.last().body)).getString("refreshToken"))
        assertNull(store.value)
    }

    @Test
    fun conBackendB8_loginResumeLogout_flujoCompleto() = runTest {
        val backend = FakeAuthBackend(b8 = true)
        val first = AuthSession(store, HttpSessionRefresher(backend, TEST_CONFIG))
        val firstRepository = repository(first, RealApiClient(backend, first, TEST_CONFIG))
        val outcome = firstRepository.login(FakeAuthBackend.EMAIL, password(FakeAuthBackend.PASSWORD))
        assertEquals(LoginOutcome.SignedIn(account), outcome)

        val restarted = AuthSession(store, HttpSessionRefresher(backend, TEST_CONFIG))
        val restartedRepository = repository(restarted, RealApiClient(backend, restarted, TEST_CONFIG))
        restartedRepository.resume()
        assertEquals(SessionState.SignedIn(account = null, offline = false), restartedRepository.sessionState.value)

        val logout = restartedRepository.logout()

        assertTrue(logout.serverRevoked)
        assertEquals(1, backend.revokedRefreshTokens.size)
        assertEquals(SessionState.SignedOut(), restartedRepository.sessionState.value)
        assertNull(store.value)
    }

    @Test
    fun conBackendSinB8_login_noCreaSesion_yLogoutNoAplica() = runTest {
        val backend = FakeAuthBackend(b8 = false)
        val noB8Session = AuthSession(store, HttpSessionRefresher(backend, TEST_CONFIG))
        val repository = repository(noB8Session, RealApiClient(backend, noB8Session, TEST_CONFIG))

        val outcome = failed(repository.login(FakeAuthBackend.EMAIL, password(FakeAuthBackend.PASSWORD)))
        val logout = repository.logout()

        assertEquals("MOBILE_CLIENT_NOT_SUPPORTED", outcome.error.code)
        assertFalse(noB8Session.hasStoredSession())
        assertTrue(logout.serverRevoked)
        assertEquals(1, backend.requests.size)
    }

    @Test
    fun conBackendB8_refreshPerdidoConGracia_laSesionContinua() = runTest {
        val backend = FakeAuthBackend(b8 = true)
        val first = AuthSession(store, HttpSessionRefresher(backend, TEST_CONFIG))
        repository(first, RealApiClient(backend, first, TEST_CONFIG))
            .login(FakeAuthBackend.EMAIL, password(FakeAuthBackend.PASSWORD))
        val restarted = AuthSession(store, HttpSessionRefresher(backend, TEST_CONFIG))
        val repository = repository(restarted, RealApiClient(backend, restarted, TEST_CONFIG))
        backend.rotating.dropNextRefreshResponse = true

        repository.resume()
        assertEquals(SessionState.SignedIn(account = null, offline = true), repository.sessionState.value)
        repository.resume()

        assertEquals(SessionState.SignedIn(account = null, offline = false), repository.sessionState.value)
    }

    private fun registerJson(codes: List<String> = List(10) { "ABCD-000$it" }): String =
        JSONObject()
            .put("user", JSONObject().put("email", "ana@example.com").put("displayName", "Ana"))
            .put("recoveryCodes", JSONArray(codes))
            .toString()

    private suspend fun RemoteAuthRepository.signUp(secret: CharArray = password("secreta1")) =
        register("Ana", "ana@example.com", secret)

    private fun failedRegister(outcome: RegisterOutcome): RegisterOutcome.Failed = outcome as RegisterOutcome.Failed

    @Test
    fun register_exitoso_haceLaPeticionCorrectaSinAuthorization() = runTest {
        transport.enqueue(response(201, registerJson()))

        repository().register("  Ana  ", "  ana@example.com ", password("secreta1"))

        val request = transport.requests.single()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("https://api.test/api/v1/auth/register", request.url)
        assertNull(request.headers["Authorization"])
        assertEquals("mobile", request.headers["X-Client"])
        val body = JSONObject(requireNotNull(request.body))
        assertEquals("Ana", body.getString("displayName"))
        assertEquals("ana@example.com", body.getString("email"))
        assertEquals("secreta1", body.getString("password"))
    }

    @Test
    fun register_exitoso_devuelveLosDiezCodigos() = runTest {
        val codes = List(10) { "ABCD-000$it" }
        transport.enqueue(response(201, registerJson(codes)))

        val outcome = repository().signUp() as RegisterOutcome.Registered

        assertEquals(codes, outcome.recoveryCodes.values)
        assertEquals(account, outcome.account)
    }

    @Test
    fun register_exitoso_noIniciaSesion() = runTest {
        transport.enqueue(response(201, registerJson()))

        repository().signUp()

        assertFalse(session.hasStoredSession())
        assertEquals(0, store.writes)
        assertNull(session.currentAccessToken())
    }

    @Test
    fun register_exitoso_noCambiaElEstadoDeSesion() = runTest {
        transport.enqueue(response(201, registerJson()))
        val repository = repository()

        repository.signUp()

        assertEquals(SessionState.SignedOut(), repository.sessionState.value)
    }

    @Test
    fun register_noTomaAuthMutex_duranteUnLoginEnVuelo() = runTest {
        val gate = CompletableDeferred<Unit>()
        transport.enqueue { gate.await(); response(200, loginJson()) }
        transport.enqueue(response(201, registerJson()))
        val repository = repository()
        val login = async { repository.login("ana@example.com", password()) }
        runCurrent()

        val outcome = repository.signUp()

        assertTrue(outcome is RegisterOutcome.Registered)
        gate.complete(Unit)
        login.await()
    }

    @Test
    fun register_conMenosDeDiezCodigos_losDevuelveTodos() = runTest {
        transport.enqueue(response(201, registerJson(listOf("AAAA-BBBB", "CCCC-DDDD", "EEEE-FFFF"))))

        val outcome = repository().signUp() as RegisterOutcome.Registered

        assertEquals(3, outcome.recoveryCodes.count)
    }

    @Test
    fun register_sinCodigos_devuelveRegisteredWithoutCodes() = runTest {
        transport.enqueue(response(201, registerJson(emptyList())))

        val outcome = repository().signUp()

        assertEquals(RegisterOutcome.RegisteredWithoutCodes("ana@example.com"), outcome)
    }

    @Test
    fun register_cuerpoIlegible_devuelveRegisteredWithoutCodes() = runTest {
        transport.enqueue(response(201, "no es json"))

        val outcome = repository().signUp()

        assertEquals(RegisterOutcome.RegisteredWithoutCodes("ana@example.com"), outcome)
    }

    @Test
    fun register_noRetieneLosCodigos() = runTest {
        val types = RemoteAuthRepository::class.java.declaredFields.map { it.type.name }

        assertTrue(types.none { it.contains("RecoveryCodes") })
    }

    @Test
    fun register_conDatosInvalidos_noHacePeticion() = runTest {
        val outcome = failedRegister(repository().register("A", "ana@example.com", password("secreta1")))

        assertEquals("DISPLAY_NAME_INVALID", outcome.error.code)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun register_conSesionActiva_noHacePeticion() = runTest {
        val repository = repository()
        repository.signIn()

        val outcome = failedRegister(repository.signUp())

        assertEquals("ALREADY_SIGNED_IN", outcome.error.code)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun register_con409_esConflictYNoIniciaSesion() = runTest {
        transport.enqueue(response(409, errorBody("EMAIL_ALREADY_REGISTERED")))

        val outcome = failedRegister(repository().signUp())

        assertTrue(outcome.error is DomainError.Conflict)
        assertFalse(session.hasStoredSession())
    }

    @Test
    fun register_con429_devuelveRateLimitedConRetryAfter() = runTest {
        transport.enqueue(response(429, errorBody("TOO_MANY_REQUESTS"), mapOf("retry-after" to "120")))

        val outcome = failedRegister(repository().signUp())

        assertTrue(outcome.error is DomainError.RateLimited)
        assertEquals(120L, outcome.retryAfterSeconds)
    }

    @Test
    fun register_sinRed_devuelveNetwork() = runTest {
        transport.enqueue(failure(TransportFailureKind.UNREACHABLE))

        assertTrue(failedRegister(repository().signUp()).error is DomainError.Network)
    }

    @Test
    fun register_con500_devuelveServer() = runTest {
        transport.enqueue(response(500, ""))

        assertTrue(failedRegister(repository().signUp()).error is DomainError.Server)
    }

    @Test
    fun register_con400_devuelveValidation() = runTest {
        transport.enqueue(response(400, errorBody("VALIDATION_ERROR")))

        assertTrue(failedRegister(repository().signUp()).error is DomainError.Validation)
    }

    @Test
    fun register_con403UntrustedOrigin_devuelveMobileClientNotSupported() = runTest {
        transport.enqueue(response(403, errorBody("UNTRUSTED_ORIGIN")))

        val outcome = failedRegister(repository().signUp())

        assertEquals("MOBILE_CLIENT_NOT_SUPPORTED", outcome.error.code)
    }

    @Test
    fun register_vaciaLaPasswordTrasExito() = runTest {
        transport.enqueue(response(201, registerJson()))
        val secret = password("secreta1")

        repository().signUp(secret)

        assertTrue(secret.all { it == '\u0000' })
    }

    @Test
    fun register_vaciaLaPasswordTrasCadaFallo() = runTest {
        val responses = listOf(
            response(409, errorBody("EMAIL_ALREADY_REGISTERED")),
            response(429, errorBody("TOO_MANY_REQUESTS")),
            failure(TransportFailureKind.TIMEOUT),
            response(500, ""),
            response(400, errorBody("VALIDATION_ERROR")),
            response(403, errorBody("UNTRUSTED_ORIGIN")),
            response(201, "ilegible"),
        )
        val repository = repository()

        responses.forEach { result ->
            transport.enqueue(result)
            val secret = password("secreta1")
            repository.signUp(secret)
            assertTrue(secret.all { it == '\u0000' })
        }
    }

    @Test
    fun register_vaciaLaPasswordSiLaValidacionFalla() = runTest {
        val secret = password("corta")

        repository().signUp(secret)

        assertTrue(secret.all { it == '\u0000' })
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun conBackendSinB8_register_funcionaYLoginPosteriorNoCreaSesion() = runTest {
        val backend = FakeAuthBackend(b8 = false)
        val noB8Session = AuthSession(store, HttpSessionRefresher(backend, TEST_CONFIG))
        val repository = repository(noB8Session, RealApiClient(backend, noB8Session, TEST_CONFIG))

        val registered = repository.register("Bea", "bea@example.com", password("secreta1")) as RegisterOutcome.Registered
        val login = repository.login("bea@example.com", password("secreta1"))

        assertEquals(10, registered.recoveryCodes.count)
        assertTrue(login is LoginOutcome.Failed)
        assertFalse(noB8Session.hasStoredSession())
    }

    @Test
    fun login_yLogout_conservanSuComportamiento() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(200, "{}"))

        val logout = repository.logout()

        assertTrue(logout.serverRevoked)
        assertEquals(SessionState.SignedOut(), repository.sessionState.value)
    }

    private val verifyUrl = "https://api.test/api/v1/auth/verify-email"
    private val resendUrl = "https://api.test/api/v1/auth/resend-verification"
    private val verifiedJson = JSONObject().put("verified", true).toString()

    private fun failedVerify(outcome: VerifyEmailOutcome) = outcome as VerifyEmailOutcome.Failed

    private fun failedResend(outcome: ResendVerificationOutcome) = outcome as ResendVerificationOutcome.Failed

    @Test
    fun verifyEmail_exitoso_hacePeticionGetSinAuthorization() = runTest {
        transport.enqueue(response(200, verifiedJson))

        repository().verifyEmail("abc+/=")

        val request = transport.requests.single()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals("$verifyUrl?token=abc%2B%2F%3D", request.url)
        assertNull(request.headers["Authorization"])
        assertEquals("mobile", request.headers["X-Client"])
    }

    @Test
    fun verifyEmail_exitoso_devuelveVerified() = runTest {
        transport.enqueue(response(200, verifiedJson))

        assertEquals(VerifyEmailOutcome.Verified, repository().verifyEmail("abc"))
    }

    @Test
    fun verifyEmail_conEnlaceCompleto_enviaSoloElToken() = runTest {
        transport.enqueue(response(200, verifiedJson))

        repository().verifyEmail("https://web.test/verificar-correo?token=abc123")

        assertEquals("$verifyUrl?token=abc123", transport.requests.single().url)
    }

    @Test
    fun verifyEmail_conTokenVacio_noHacePeticion() = runTest {
        val outcome = failedVerify(repository().verifyEmail("   "))

        assertTrue(outcome.error is DomainError.Validation)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun verifyEmail_con400_esValidationYNoVerifica() = runTest {
        transport.enqueue(response(400, errorBody("INVALID_OR_EXPIRED_TOKEN")))

        val outcome = repository().verifyEmail("abc")

        assertTrue(failedVerify(outcome).error is DomainError.Validation)
    }

    @Test
    fun verifyEmail_cuerpoIlegible_esServer() = runTest {
        transport.enqueue(response(200, "<html>"))

        assertTrue(failedVerify(repository().verifyEmail("abc")).error is DomainError.Server)
    }

    @Test
    fun verifyEmail_conVerifiedFalse_esServer() = runTest {
        transport.enqueue(response(200, """{"verified":false}"""))

        assertTrue(failedVerify(repository().verifyEmail("abc")).error is DomainError.Server)
    }

    @Test
    fun verifyEmail_con429_devuelveRateLimitedConRetryAfter() = runTest {
        transport.enqueue(response(429, errorBody("TOO_MANY_REQUESTS"), mapOf("retry-after" to "90")))

        val outcome = failedVerify(repository().verifyEmail("abc"))

        assertTrue(outcome.error is DomainError.RateLimited)
        assertEquals(90L, outcome.retryAfterSeconds)
    }

    @Test
    fun verifyEmail_sinRed_devuelveNetwork() = runTest {
        transport.enqueue(failure(TransportFailureKind.UNREACHABLE))

        assertTrue(failedVerify(repository().verifyEmail("abc")).error is DomainError.Network)
    }

    @Test
    fun verifyEmail_con500_devuelveServer() = runTest {
        transport.enqueue(response(500, ""))

        assertTrue(failedVerify(repository().verifyEmail("abc")).error is DomainError.Server)
    }

    @Test
    fun verifyEmail_noCambiaElEstadoDeSesionNiTocaElAlmacen() = runTest {
        val repository = repository()
        repository.signIn()
        val before = repository.sessionState.value
        transport.enqueue(response(200, verifiedJson))

        repository.verifyEmail("abc")

        assertEquals(before, repository.sessionState.value)
        assertEquals("r1", store.value)
        assertNull(transport.requests.last().headers["Authorization"])
    }

    @Test
    fun verifyEmail_noExponeElTokenEnToStringNiErrores() = runTest {
        transport.enqueue(response(400, errorBody("INVALID_OR_EXPIRED_TOKEN")))

        val outcome = repository().verifyEmail("secreto-abc")

        assertFalse(outcome.toString().contains("secreto-abc"))
        assertFalse(transport.requests.single().toString().contains("secreto-abc"))
    }

    @Test
    fun resend_conSesion_hacePostConAuthorizationSinCuerpo() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(200, """{"alreadyVerified":false}"""))

        repository.resendVerification()

        val request = transport.requests.last()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals(resendUrl, request.url)
        assertEquals("Bearer a1", request.headers["Authorization"])
        assertNull(request.body)
    }

    @Test
    fun resend_sinSesion_noHacePeticion() = runTest {
        val outcome = failedResend(repository().resendVerification())

        assertEquals(DomainError.Unauthenticated::class, outcome.error::class)
        assertEquals("NOT_SIGNED_IN", outcome.error.code)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun resend_alreadyVerifiedFalse_esSent() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(200, """{"alreadyVerified":false,"emailVerificationToken":"dev"}"""))

        assertEquals(ResendVerificationOutcome.Sent, repository.resendVerification())
    }

    @Test
    fun resend_alreadyVerifiedTrue_esAlreadyVerified() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(200, """{"alreadyVerified":true}"""))

        assertEquals(ResendVerificationOutcome.AlreadyVerified, repository.resendVerification())
    }

    @Test
    fun resend_con503_devuelveServerConCodigoDeSuspension() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(503, errorBody("EMAIL_VERIFICATION_DISABLED")))

        val error = failedResend(repository.resendVerification()).error

        assertTrue(error is DomainError.Server)
        assertEquals("EMAIL_VERIFICATION_DISABLED", error.code)
    }

    @Test
    fun resend_con429_devuelveRateLimitedConRetryAfter() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(429, errorBody("TOO_MANY_REQUESTS"), mapOf("retry-after" to "120")))

        val outcome = failedResend(repository.resendVerification())

        assertTrue(outcome.error is DomainError.RateLimited)
        assertEquals(120L, outcome.retryAfterSeconds)
    }

    @Test
    fun resend_con401_sinRenovacionPosible_devuelveUnauthenticated() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(401, errorBody("UNAUTHENTICATED")))
        refresher.enqueue(RefreshOutcome.Rejected(DomainError.Unauthenticated("INVALID_REFRESH_TOKEN", "x")))

        val outcome = failedResend(repository.resendVerification())

        assertTrue(outcome.error is DomainError.Unauthenticated)
    }

    @Test
    fun resend_con403UntrustedOrigin_devuelveMobileClientNotSupported() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(403, errorBody("UNTRUSTED_ORIGIN")))

        assertEquals("MOBILE_CLIENT_NOT_SUPPORTED", failedResend(repository.resendVerification()).error.code)
    }

    @Test
    fun resend_cuerpoIlegible_esServer() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(200, "<html>"))

        assertTrue(failedResend(repository.resendVerification()).error is DomainError.Server)
    }

    @Test
    fun conBackendB8_verificarYReenviar_extremoAExtremo() = runTest {
        val backend = FakeAuthBackend(b8 = true)
        backend.validVerificationTokens += "tok-1"
        val auth = AuthSession(store, HttpSessionRefresher(backend, TEST_CONFIG))
        val repository = repository(auth, RealApiClient(backend, auth, TEST_CONFIG))

        assertEquals(VerifyEmailOutcome.Verified, repository.verifyEmail("tok-1"))
        assertTrue(failedVerify(repository.verifyEmail("tok-1")).error is DomainError.Validation)
        repository.login(FakeAuthBackend.EMAIL, password(FakeAuthBackend.PASSWORD))
        assertEquals(ResendVerificationOutcome.Sent, repository.resendVerification())
        backend.verificationEnabled = false
        assertEquals("EMAIL_VERIFICATION_DISABLED", failedResend(repository.resendVerification()).error.code)
        backend.resendAlreadyVerified = true
        assertEquals(ResendVerificationOutcome.AlreadyVerified, repository.resendVerification())
    }
}
