package com.simposio.taskeer.data.remote.auth

import com.simposio.taskeer.domain.auth.ResetPasswordOutcome
import com.simposio.taskeer.domain.auth.RegenerateCodesOutcome
import com.simposio.taskeer.domain.auth.ChangePasswordOutcome
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
import com.simposio.taskeer.domain.auth.GoogleCredential
import com.simposio.taskeer.domain.auth.GoogleSignInOutcome
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

    // Por defecto el canje devuelve el mismo texto del ID token: así FakeAuthBackend distingue la cuenta por el token.
    private class FakeGoogleExchange : GoogleTokenExchange {
        val credentials = mutableListOf<GoogleCredential>()
        var result: ((GoogleCredential) -> GoogleExchangeResult) = { GoogleExchangeResult.Token(it.idToken) }

        override suspend fun exchange(credential: GoogleCredential): GoogleExchangeResult {
            credentials += credential
            return result(credential)
        }
    }

    private val exchange = FakeGoogleExchange()

    private fun TestScope.repository(
        authSession: AuthSession = session,
        apiClient: RealApiClient = client,
        googleExchange: GoogleTokenExchange? = exchange,
    ) = RemoteAuthRepository(apiClient, authSession, backgroundScope + UnconfinedTestDispatcher(testScheduler), googleExchange)

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
        val parameterTypes = RemoteAuthRepository::class.java.constructors.filterNot { it.isSynthetic }.single().parameterTypes.map { it.name }

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

    private val resetUrl = "https://api.test/api/v1/auth/reset-password-with-code"
    private val changeUrl = "https://api.test/api/v1/auth/change-password"
    private val regenerateUrl = "https://api.test/api/v1/auth/recovery-codes/regenerate"

    private fun failedReset(outcome: ResetPasswordOutcome) = outcome as ResetPasswordOutcome.Failed

    private fun failedChange(outcome: ChangePasswordOutcome) = outcome as ChangePasswordOutcome.Failed

    private fun failedRegenerate(outcome: RegenerateCodesOutcome) = outcome as RegenerateCodesOutcome.Failed

    private val resetOk = """{"reset":true}"""

    private fun changeJson(refresh: String? = "r2", access: String = "a2"): String {
        val json = JSONObject().put("accessToken", access)
            .put("user", JSONObject().put("email", "ana@example.com").put("displayName", "Ana").put("hasPassword", true))
        if (refresh != null) json.put("refreshToken", refresh)
        return json.toString()
    }

    private fun codesJson(count: Int = 10) =
        JSONObject().put("recoveryCodes", JSONArray(List(count) { "NEW1-%04d".format(it) })).toString()

    @Test
    fun resetPassword_exitoso_hacePostSinAuthorizationConElJsonEsperado() = runTest {
        transport.enqueue(response(200, resetOk))

        val outcome = repository().resetPassword("  ana@example.com ", "abcd-ef12", password("nueva-clave"))

        val request = transport.requests.single()
        assertEquals(ResetPasswordOutcome.Reset, outcome)
        assertEquals(HttpMethod.POST, request.method)
        assertEquals(resetUrl, request.url)
        assertNull(request.headers["Authorization"])
        assertEquals("mobile", request.headers["X-Client"])
        val body = JSONObject(requireNotNull(request.body))
        assertEquals("ana@example.com", body.getString("email"))
        assertEquals("ABCD-EF12", body.getString("code"))
        assertEquals("nueva-clave", body.getString("newPassword"))
    }

    @Test
    fun resetPassword_conDatosInvalidos_noHacePeticion() = runTest {
        val repository = repository()

        val badEmail = failedReset(repository.resetPassword("sin-arroba", "ABCD-EF12", password("nueva-clave")))
        val badCode = failedReset(repository.resetPassword("ana@example.com", "ABC", password("nueva-clave")))
        val badPassword = failedReset(repository.resetPassword("ana@example.com", "ABCD-EF12", password("corta")))

        assertEquals("EMAIL_INVALID", badEmail.error.code)
        assertEquals("CODE_INVALID", badCode.error.code)
        assertEquals("PASSWORD_TOO_SHORT", badPassword.error.code)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun resetPassword_con400_esValidationYNoCambiaNada() = runTest {
        transport.enqueue(response(400, errorBody("INVALID_RECOVERY_CODE")))
        val repository = repository()

        val outcome = failedReset(repository.resetPassword("ana@example.com", "ABCD-EF12", password("nueva-clave")))

        assertTrue(outcome.error is DomainError.Validation)
        assertEquals("INVALID_RECOVERY_CODE", outcome.error.code)
        assertEquals(SessionState.SignedOut(), repository.sessionState.value)
        assertNull(store.value)
    }

    @Test
    fun resetPassword_cuerpoIlegible_esReset() = runTest {
        transport.enqueue(response(200, "<html>"))

        assertEquals(ResetPasswordOutcome.Reset, repository().resetPassword("ana@example.com", "ABCD-EF12", password("nueva-clave")))
    }

    @Test
    fun resetPassword_haceUnaSolaPeticionPorLlamada() = runTest {
        transport.enqueue(response(400, errorBody("INVALID_RECOVERY_CODE")))

        repository().resetPassword("ana@example.com", "ABCD-EF12", password("nueva-clave"))

        assertEquals(1, transport.requests.size)
    }

    @Test
    fun resetPassword_noCambiaSesionNiAlmacen() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(200, resetOk))

        repository.resetPassword("ana@example.com", "ABCD-EF12", password("nueva-clave"))

        assertEquals(SessionState.SignedIn(account, offline = false), repository.sessionState.value)
        assertEquals("r1", store.value)
        assertEquals("a1", session.currentAccessToken())
    }

    @Test
    fun resetPassword_con429_devuelveRateLimitedConRetryAfter() = runTest {
        transport.enqueue(response(429, errorBody("TOO_MANY_REQUESTS"), mapOf("retry-after" to "300")))

        val outcome = failedReset(repository().resetPassword("ana@example.com", "ABCD-EF12", password("nueva-clave")))

        assertTrue(outcome.error is DomainError.RateLimited)
        assertEquals(300L, outcome.retryAfterSeconds)
    }

    @Test
    fun resetPassword_sinRed_devuelveNetwork() = runTest {
        transport.enqueue(failure(TransportFailureKind.UNREACHABLE))

        val outcome = failedReset(repository().resetPassword("ana@example.com", "ABCD-EF12", password("nueva-clave")))

        assertTrue(outcome.error is DomainError.Network)
    }

    @Test
    fun resetPassword_con500_devuelveServer() = runTest {
        transport.enqueue(response(500, errorBody("INTERNAL")))

        val outcome = failedReset(repository().resetPassword("ana@example.com", "ABCD-EF12", password("nueva-clave")))

        assertTrue(outcome.error is DomainError.Server)
    }

    @Test
    fun resetPassword_rellenaDeCerosLaContrasena() = runTest {
        val ok = password("nueva-clave")
        val invalid = password("corta")
        val failing = password("nueva-clave")
        transport.enqueue(response(200, resetOk))
        transport.enqueue(response(400, errorBody("INVALID_RECOVERY_CODE")))
        val repository = repository()

        repository.resetPassword("ana@example.com", "ABCD-EF12", ok)
        repository.resetPassword("ana@example.com", "ABCD-EF12", invalid)
        repository.resetPassword("ana@example.com", "ABCD-EF12", failing)

        assertTrue(ok.all { it == '\u0000' })
        assertTrue(invalid.all { it == '\u0000' })
        assertTrue(failing.all { it == '\u0000' })
    }

    private fun TestScope.backendRepository(backend: FakeAuthBackend): RemoteAuthRepository {
        val auth = AuthSession(store, HttpSessionRefresher(backend, TEST_CONFIG))
        return repository(auth, RealApiClient(backend, auth, TEST_CONFIG))
    }

    @Test
    fun resetPassword_conCodigoValido_soloEseQuedaUsadoYLosOtros9Siguen() = runTest {
        val backend = FakeAuthBackend(b8 = true)
        val repository = backendRepository(backend)

        val outcome = repository.resetPassword(FakeAuthBackend.EMAIL, "abcd-0003", password("nueva-clave"))

        assertEquals(ResetPasswordOutcome.Reset, outcome)
        assertEquals(setOf("ABCD-0003"), backend.usedCodes)
        assertEquals(9, backend.recoveryCodes.count { it !in backend.usedCodes })
        assertEquals(
            ResetPasswordOutcome.Reset,
            repository.resetPassword(FakeAuthBackend.EMAIL, "ABCD-0004", password("otra-clave-1")),
        )
    }

    @Test
    fun resetPassword_conCorreoInexistenteOCodigoInvalido_respondeIgual() = runTest {
        val backend = FakeAuthBackend(b8 = true)
        val repository = backendRepository(backend)
        repository.resetPassword(FakeAuthBackend.EMAIL, "ABCD-0001", password("nueva-clave"))

        val unknownEmail = failedReset(repository.resetPassword("otra@example.com", "ABCD-0002", password("nueva-clave")))
        val unknownCode = failedReset(repository.resetPassword(FakeAuthBackend.EMAIL, "ZZZZ-9999", password("nueva-clave")))
        val usedCode = failedReset(repository.resetPassword(FakeAuthBackend.EMAIL, "ABCD-0001", password("nueva-clave")))

        assertEquals(unknownEmail, unknownCode)
        assertEquals(unknownCode, usedCode)
        assertEquals("INVALID_RECOVERY_CODE", unknownEmail.error.code)
    }

    @Test
    fun resetPassword_revocaLosRefreshDelUsuario() = runTest {
        val backend = FakeAuthBackend(b8 = true)
        val repository = backendRepository(backend)
        repository.login(FakeAuthBackend.EMAIL, password(FakeAuthBackend.PASSWORD))

        repository.resetPassword(FakeAuthBackend.EMAIL, "ABCD-0000", password("nueva-clave"))
        repository.resume()
        backend.rotating.expireAccess("access-login")
        val auth = AuthSession(store, HttpSessionRefresher(backend, TEST_CONFIG))

        assertTrue(auth.renew(null) is com.simposio.taskeer.data.remote.session.RenewOutcome.SessionExpired)
    }

    @Test
    fun changePassword_conSesion_hacePostConAuthorizationYElJsonEsperado() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(200, changeJson()))

        repository.changePassword(password("vieja-clave"), password("nueva-clave"))

        val request = transport.requests.last()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals(changeUrl, request.url)
        assertEquals("Bearer a1", request.headers["Authorization"])
        val body = JSONObject(requireNotNull(request.body))
        assertEquals("vieja-clave", body.getString("currentPassword"))
        assertEquals("nueva-clave", body.getString("newPassword"))
    }

    @Test
    fun changePassword_sinSesion_noHacePeticion() = runTest {
        val outcome = failedChange(repository().changePassword(password("vieja-clave"), password("nueva-clave")))

        assertEquals("NOT_SIGNED_IN", outcome.error.code)
        assertTrue(outcome.error is DomainError.Unauthenticated)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun changePassword_exitoso_guardaElParNuevoYPublicaLaCuenta() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(200, changeJson(refresh = "r2", access = "a2")))

        val outcome = repository.changePassword(password("vieja-clave"), password("nueva-clave"))

        val expected = AccountSummary("ana@example.com", "Ana", hasPassword = true)
        assertEquals(ChangePasswordOutcome.Changed(expected), outcome)
        assertEquals("a2", session.currentAccessToken())
        assertEquals("r2", store.value)
        assertEquals(SessionState.SignedIn(expected, offline = false), repository.sessionState.value)
    }

    @Test
    fun changePassword_sinRefreshToken_cierraLaSesionLocalYDevuelveChangedSignedOut() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(200, changeJson(refresh = null)))

        val outcome = repository.changePassword(password("vieja-clave"), password("nueva-clave"))

        assertEquals(ChangePasswordOutcome.ChangedSignedOut, outcome)
        assertNull(store.value)
        assertEquals(SessionState.SignedOut(), repository.sessionState.value)
        assertEquals(1, transport.requests.count { it.url == changeUrl })
        assertTrue(transport.requests.none { it.url.endsWith("auth/logout") })
    }

    @Test
    fun changePassword_cuerpoIlegible_cierraLaSesionLocal() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(200, "<html>"))

        val outcome = repository.changePassword(password("vieja-clave"), password("nueva-clave"))

        assertEquals(ChangePasswordOutcome.ChangedSignedOut, outcome)
        assertNull(store.value)
        assertEquals(SessionState.SignedOut(), repository.sessionState.value)
    }

    @Test
    fun changePassword_con401InvalidCredentials_noCierraLaSesionNiRenueva() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(401, errorBody("INVALID_CREDENTIALS")))

        val outcome = failedChange(repository.changePassword(password("mal-clave-1"), password("nueva-clave")))

        assertEquals("INVALID_CREDENTIALS", outcome.error.code)
        assertTrue(refresher.receivedTokens.isEmpty())
        assertEquals(SessionState.SignedIn(account, offline = false), repository.sessionState.value)
        assertEquals("r1", store.value)
    }

    @Test
    fun changePassword_con401Unauthenticated_devuelveUnauthenticated() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(401, errorBody("UNAUTHENTICATED")))
        refresher.enqueue(RefreshOutcome.Rejected(DomainError.Unauthenticated("INVALID_REFRESH_TOKEN", "x")))

        val outcome = failedChange(repository.changePassword(password("vieja-clave"), password("nueva-clave")))

        assertTrue(outcome.error is DomainError.Unauthenticated)
    }

    @Test
    fun changePassword_con400NoLocalPassword_devuelveValidationConElCodigo() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(400, errorBody("NO_LOCAL_PASSWORD")))

        val outcome = failedChange(repository.changePassword(password("vieja-clave"), password("nueva-clave")))

        assertTrue(outcome.error is DomainError.Validation)
        assertEquals("NO_LOCAL_PASSWORD", outcome.error.code)
    }

    @Test
    fun changePassword_con409_conservaElCodigoConcurrentPasswordChange() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(409, errorBody("CONCURRENT_PASSWORD_CHANGE")))

        val outcome = failedChange(repository.changePassword(password("vieja-clave"), password("nueva-clave")))

        assertEquals("CONCURRENT_PASSWORD_CHANGE", outcome.error.code)
        assertEquals(SessionState.SignedIn(account, offline = false), repository.sessionState.value)
    }

    @Test
    fun changePassword_con429_devuelveRateLimitedConRetryAfter() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(429, errorBody("TOO_MANY_REQUESTS"), mapOf("retry-after" to "120")))

        val outcome = failedChange(repository.changePassword(password("vieja-clave"), password("nueva-clave")))

        assertTrue(outcome.error is DomainError.RateLimited)
        assertEquals(120L, outcome.retryAfterSeconds)
    }

    @Test
    fun changePassword_rellenaDeCerosAmbasContrasenas() = runTest {
        val repository = repository()
        val current = password("vieja-clave")
        val new = password("nueva-clave")

        repository.changePassword(current, new)

        assertTrue(current.all { it == '\u0000' })
        assertTrue(new.all { it == '\u0000' })
    }

    @Test
    fun changePassword_noExponeSecretosEnToStringNiErrores() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(401, errorBody("INVALID_CREDENTIALS")))

        val outcome = repository.changePassword(password("vieja-secreta"), password("nueva-secreta"))

        assertFalse(outcome.toString().contains("secreta"))
        assertFalse(transport.requests.last().toString().contains("secreta"))
    }

    @Test
    fun regenerate_conSesion_hacePostConAuthorizationYContrasena() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(200, codesJson()))

        repository.regenerateRecoveryCodes(password("vieja-clave"))

        val request = transport.requests.last()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals(regenerateUrl, request.url)
        assertEquals("Bearer a1", request.headers["Authorization"])
        assertEquals("vieja-clave", JSONObject(requireNotNull(request.body)).getString("currentPassword"))
    }

    @Test
    fun regenerate_sinSesion_noHacePeticion() = runTest {
        val outcome = failedRegenerate(repository().regenerateRecoveryCodes(password("vieja-clave")))

        assertEquals("NOT_SIGNED_IN", outcome.error.code)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun regenerate_sinContrasena_noHacePeticion() = runTest {
        val repository = repository()
        repository.signIn()
        val before = transport.requests.size

        val outcome = failedRegenerate(repository.regenerateRecoveryCodes(charArrayOf()))

        assertEquals("PASSWORD_REQUIRED", outcome.error.code)
        assertEquals(before, transport.requests.size)
    }

    @Test
    fun regenerate_exitoso_devuelve10Codigos() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(200, codesJson()))

        val outcome = repository.regenerateRecoveryCodes(password("vieja-clave")) as RegenerateCodesOutcome.Regenerated

        assertEquals(10, outcome.codes.count)
        assertEquals("NEW1-0000", outcome.codes.values.first())
    }

    @Test
    fun regenerate_sinCodigos_esRegeneratedWithoutCodes() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(200, codesJson(count = 0)))
        transport.enqueue(response(200, "<html>"))

        assertEquals(RegenerateCodesOutcome.RegeneratedWithoutCodes, repository.regenerateRecoveryCodes(password("vieja-clave")))
        assertEquals(RegenerateCodesOutcome.RegeneratedWithoutCodes, repository.regenerateRecoveryCodes(password("vieja-clave")))
    }

    @Test
    fun regenerate_con401InvalidCredentials_noCierraLaSesion() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(401, errorBody("INVALID_CREDENTIALS")))

        val outcome = failedRegenerate(repository.regenerateRecoveryCodes(password("mal-clave-1")))

        assertEquals("INVALID_CREDENTIALS", outcome.error.code)
        assertEquals(SessionState.SignedIn(account, offline = false), repository.sessionState.value)
        assertTrue(refresher.receivedTokens.isEmpty())
    }

    @Test
    fun regenerate_con400NoLocalPassword_devuelveElCodigo() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(400, errorBody("NO_LOCAL_PASSWORD")))

        assertEquals("NO_LOCAL_PASSWORD", failedRegenerate(repository.regenerateRecoveryCodes(password("vieja-clave"))).error.code)
    }

    @Test
    fun regenerate_con429_devuelveRateLimitedConRetryAfter() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(429, errorBody("TOO_MANY_REQUESTS"), mapOf("retry-after" to "60")))

        val outcome = failedRegenerate(repository.regenerateRecoveryCodes(password("vieja-clave")))

        assertTrue(outcome.error is DomainError.RateLimited)
        assertEquals(60L, outcome.retryAfterSeconds)
    }

    @Test
    fun regenerate_rellenaDeCerosLaContrasena() = runTest {
        val repository = repository()
        repository.signIn()
        transport.enqueue(response(200, codesJson()))
        val ok = password("vieja-clave")
        val noSession = password("vieja-clave")

        repository.regenerateRecoveryCodes(ok)
        repository().regenerateRecoveryCodes(noSession)

        assertTrue(ok.all { it == '\u0000' })
        assertTrue(noSession.all { it == '\u0000' })
    }

    @Test
    fun regenerate_conElServidor_invalidaTodosLosCodigosViejos() = runTest {
        val backend = FakeAuthBackend(b8 = true)
        val repository = backendRepository(backend)
        repository.login(FakeAuthBackend.EMAIL, password(FakeAuthBackend.PASSWORD))
        repository.resetPassword(FakeAuthBackend.EMAIL, "ABCD-0000", password("nueva-clave"))
        repository.login(FakeAuthBackend.EMAIL, password("nueva-clave"))

        val outcome = repository.regenerateRecoveryCodes(password("nueva-clave"))

        assertTrue(outcome is RegenerateCodesOutcome.Regenerated)
        val old = failedReset(repository.resetPassword(FakeAuthBackend.EMAIL, "ABCD-0005", password("otra-clave-1")))
        assertEquals("INVALID_RECOVERY_CODE", old.error.code)
        val oldUsed = failedReset(repository.resetPassword(FakeAuthBackend.EMAIL, "ABCD-0000", password("otra-clave-1")))
        assertEquals("INVALID_RECOVERY_CODE", oldUsed.error.code)
        assertEquals(
            ResetPasswordOutcome.Reset,
            repository.resetPassword(FakeAuthBackend.EMAIL, "NEW1-0002", password("otra-clave-1")),
        )
    }

    @Test
    fun changePassword_conElServidor_sustituyeLaSesionYRevocaLaAnterior() = runTest {
        val backend = FakeAuthBackend(b8 = true)
        val repository = backendRepository(backend)
        repository.login(FakeAuthBackend.EMAIL, password(FakeAuthBackend.PASSWORD))

        val outcome = repository.changePassword(password(FakeAuthBackend.PASSWORD), password("nueva-clave"))

        assertTrue(outcome is ChangePasswordOutcome.Changed)
        assertEquals("refresh-change", store.value)
        assertTrue("refresh-login" in backend.revokedRefreshTokens)
        assertEquals("nueva-clave", backend.password)
    }

    @Test
    fun changePassword_conElServidorSinRefreshEnElCuerpo_dejaLaSesionCerrada() = runTest {
        val backend = FakeAuthBackend(b8 = true)
        backend.changeReturnsRefresh = false
        val repository = backendRepository(backend)
        repository.login(FakeAuthBackend.EMAIL, password(FakeAuthBackend.PASSWORD))

        val outcome = repository.changePassword(password(FakeAuthBackend.PASSWORD), password("nueva-clave"))

        assertEquals(ChangePasswordOutcome.ChangedSignedOut, outcome)
        assertNull(store.value)
        assertEquals(SessionState.SignedOut(), repository.sessionState.value)
    }

    private fun googleCredential(token: String = "id-token-secreto", nonce: String = "nonce-claro") =
        GoogleCredential(token, nonce)

    private fun googleJson(
        refresh: String? = "r1",
        codes: List<String>? = null,
        hasPassword: Boolean? = null,
    ): String {
        val user = JSONObject().put("email", "ana@example.com").put("displayName", "Ana")
        if (hasPassword != null) user.put("hasPassword", hasPassword)
        val json = JSONObject().put("accessToken", "a1").put("user", user)
        if (refresh != null) json.put("refreshToken", refresh)
        if (codes != null) json.put("recoveryCodes", JSONArray(codes))
        return json.toString()
    }

    private fun failedGoogle(outcome: GoogleSignInOutcome): GoogleSignInOutcome.Failed =
        outcome as GoogleSignInOutcome.Failed

    @Test
    fun google_cuerpoDelBackend_esSupabaseAccessToken() = runTest {
        transport.enqueue(response(200, googleJson()))
        exchange.result = { GoogleExchangeResult.Token("supabase-jwt") }

        repository().signInWithGoogle(googleCredential())

        val request = transport.requests.single()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("https://api.test/api/v1/auth/google/mobile", request.url)
        assertNull(request.headers["Authorization"])
        assertEquals("mobile", request.headers["X-Client"])
        val body = JSONObject(requireNotNull(request.body))
        assertEquals("supabase-jwt", body.getString("supabaseAccessToken"))
        assertFalse(body.has("idToken"))
        assertFalse(body.has("nonce"))
        assertEquals(1, body.length())
    }

    @Test
    fun google_canjeaYLuegoEntregaAlBackend() = runTest {
        transport.enqueue(response(200, googleJson()))

        val outcome = repository().signInWithGoogle(googleCredential(token = "id-1", nonce = "nonce-1"))

        val credential = exchange.credentials.single()
        assertEquals("id-1", credential.idToken)
        assertEquals("nonce-1", credential.rawNonce)
        assertEquals(1, transport.requests.size)
        assertTrue(outcome is GoogleSignInOutcome.SignedIn)
    }

    @Test
    fun google_siFallaElCanje_noLlamaAlBackend() = runTest {
        val failed = DomainError.Unauthenticated("INVALID_GOOGLE_TOKEN", "x")
        exchange.result = { GoogleExchangeResult.Failed(failed, retryAfterSeconds = 7) }
        val repository = repository()

        val outcome = failedGoogle(repository.signInWithGoogle(googleCredential()))

        assertEquals(failed, outcome.error)
        assertEquals(7L, outcome.retryAfterSeconds)
        assertTrue(transport.requests.isEmpty())
        assertEquals(SessionState.SignedOut(), repository.sessionState.value)
        assertFalse(session.hasStoredSession())
    }

    @Test
    fun google_sinCanjeConfigurado_noHacePeticion() = runTest {
        val outcome = failedGoogle(repository(googleExchange = null).signInWithGoogle(googleCredential()))

        assertEquals("GOOGLE_EXCHANGE_NOT_CONFIGURED", outcome.error.code)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun google_conCodigos_devuelveNewAccountCodes() = runTest {
        transport.enqueue(response(200, googleJson(codes = listOf("ABCD-0001", "ABCD-0002"))))

        val outcome = repository().signInWithGoogle(googleCredential()) as GoogleSignInOutcome.SignedIn

        assertEquals(listOf("ABCD-0001", "ABCD-0002"), outcome.newAccountCodes?.values)
    }

    @Test
    fun google_conRefreshTokenExpiresAt_iniciaSesion() = runTest {
        val body = JSONObject(googleJson()).put("refreshTokenExpiresAt", "2026-12-26T00:00:00.000Z").toString()
        transport.enqueue(response(200, body))
        val repository = repository()

        val outcome = repository.signInWithGoogle(googleCredential())

        assertTrue(outcome is GoogleSignInOutcome.SignedIn)
        assertEquals("r1", store.value)
    }

    @Test
    fun google_sinRefreshToken_esNoSoportado() = runTest {
        transport.enqueue(response(200, googleJson(refresh = null)))

        val outcome = failedGoogle(repository().signInWithGoogle(googleCredential()))

        assertEquals("MOBILE_CLIENT_NOT_SUPPORTED", outcome.error.code)
    }

    @Test
    fun signInWithGoogle_conSesion_noHacePeticion() = runTest {
        val repository = repository()
        repository.signIn()

        val outcome = failedGoogle(repository.signInWithGoogle(googleCredential()))

        assertEquals("ALREADY_SIGNED_IN", outcome.error.code)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun google_yaConSesion_noCanjea() = runTest {
        val repository = repository()
        repository.signIn()

        repository.signInWithGoogle(googleCredential())

        assertTrue(exchange.credentials.isEmpty())
    }

    @Test
    fun signInWithGoogle_haceUnaSolaPeticionPorLlamada() = runTest {
        transport.enqueue(response(500, ""))

        repository().signInWithGoogle(googleCredential())

        assertEquals(1, transport.requests.size)
    }

    @Test
    fun signInWithGoogle_soloTocaElHostDeLaApi() = runTest {
        transport.enqueue(response(200, googleJson()))

        repository().signInWithGoogle(googleCredential())

        assertTrue(transport.requests.all { it.url.startsWith("https://api.test/") })
    }

    @Test
    fun signInWithGoogle_exitoso_guardaElParYPublicaLaCuenta() = runTest {
        transport.enqueue(response(200, googleJson(refresh = "r1")))
        val repository = repository()

        val outcome = repository.signInWithGoogle(googleCredential())

        assertEquals("a1", session.currentAccessToken())
        assertEquals("r1", store.value)
        assertEquals(account, (outcome as GoogleSignInOutcome.SignedIn).account)
        assertEquals(SessionState.SignedIn(account, offline = false), repository.sessionState.value)
    }

    @Test
    fun signInWithGoogle_conCodigos_devuelveLosCodigos() = runTest {
        transport.enqueue(response(200, googleJson(codes = listOf("ABCD-0001", "ABCD-0002"))))

        val outcome = repository().signInWithGoogle(googleCredential()) as GoogleSignInOutcome.SignedIn

        assertEquals(listOf("ABCD-0001", "ABCD-0002"), outcome.newAccountCodes?.values)
    }

    @Test
    fun signInWithGoogle_sinCodigos_devuelveNull() = runTest {
        transport.enqueue(response(200, googleJson(codes = null)))

        val absent = repository().signInWithGoogle(googleCredential()) as GoogleSignInOutcome.SignedIn

        assertNull(absent.newAccountCodes)
    }

    @Test
    fun signInWithGoogle_conCodigosVacios_devuelveNull() = runTest {
        transport.enqueue(response(200, googleJson(codes = emptyList())))

        val outcome = repository().signInWithGoogle(googleCredential()) as GoogleSignInOutcome.SignedIn

        assertNull(outcome.newAccountCodes)
    }

    @Test
    fun signInWithGoogle_sinRefreshToken_noIniciaSesion() = runTest {
        transport.enqueue(response(200, googleJson(refresh = null)))
        val repository = repository()

        val outcome = failedGoogle(repository.signInWithGoogle(googleCredential()))

        assertEquals("MOBILE_CLIENT_NOT_SUPPORTED", outcome.error.code)
        assertEquals(0, store.writes)
        assertEquals(SessionState.SignedOut(), repository.sessionState.value)
    }

    @Test
    fun signInWithGoogle_cuerpoIlegible_noIniciaSesion() = runTest {
        transport.enqueue(response(200, "no es json"))
        val repository = repository()

        val outcome = failedGoogle(repository.signInWithGoogle(googleCredential()))

        assertEquals("INVALID_LOGIN_RESPONSE", outcome.error.code)
        assertFalse(session.hasStoredSession())
        assertEquals(SessionState.SignedOut(), repository.sessionState.value)
    }

    @Test
    fun signInWithGoogle_noGuardaElIdTokenEnElAlmacen() = runTest {
        transport.enqueue(response(200, googleJson()))

        repository().signInWithGoogle(googleCredential(token = "id-token-secreto"))

        assertEquals("r1", store.value)
        assertFalse(store.value.orEmpty().contains("id-token-secreto"))
        assertEquals("a1", session.currentAccessToken())
    }

    @Test
    fun signInWithGoogle_conHasPasswordFalse_publicaLaCuentaSinContrasena() = runTest {
        transport.enqueue(response(200, googleJson(hasPassword = false)))
        val repository = repository()

        repository.signInWithGoogle(googleCredential())

        val signedIn = repository.sessionState.value as SessionState.SignedIn
        assertEquals(false, signedIn.account?.hasPassword)
    }

    @Test
    fun signInWithGoogle_con401InvalidGoogleToken_esUnauthenticatedYNoRenueva() = runTest {
        transport.enqueue(response(401, errorBody("INVALID_GOOGLE_TOKEN")))
        val repository = repository()

        val outcome = failedGoogle(repository.signInWithGoogle(googleCredential()))

        assertTrue(outcome.error is DomainError.Unauthenticated)
        assertEquals("INVALID_GOOGLE_TOKEN", outcome.error.code)
        assertTrue(refresher.receivedTokens.isEmpty())
        assertEquals(SessionState.SignedOut(), repository.sessionState.value)
    }

    @Test
    fun signInWithGoogle_con403_esForbiddenYNoIniciaSesion() = runTest {
        transport.enqueue(response(403, errorBody("GOOGLE_EMAIL_NOT_VERIFIED")))

        val outcome = failedGoogle(repository().signInWithGoogle(googleCredential()))

        assertTrue(outcome.error is DomainError.Forbidden)
        assertEquals("GOOGLE_EMAIL_NOT_VERIFIED", outcome.error.code)
        assertFalse(session.hasStoredSession())
    }

    @Test
    fun signInWithGoogle_con409_noIniciaSesionNiReintenta() = runTest {
        transport.enqueue(response(409, errorBody("LOCAL_ACCOUNT_EMAIL_NOT_VERIFIED")))
        val repository = repository()

        val outcome = failedGoogle(repository.signInWithGoogle(googleCredential()))

        assertTrue(outcome.error is DomainError.Conflict)
        assertEquals(1, transport.requests.size)
        assertFalse(session.hasStoredSession())
        assertEquals(SessionState.SignedOut(), repository.sessionState.value)
    }

    @Test
    fun signInWithGoogle_con429_devuelveRateLimitedConRetryAfter() = runTest {
        transport.enqueue(response(429, errorBody("TOO_MANY_REQUESTS"), mapOf("retry-after" to "120")))

        val outcome = failedGoogle(repository().signInWithGoogle(googleCredential()))

        assertTrue(outcome.error is DomainError.RateLimited)
        assertEquals(120L, outcome.retryAfterSeconds)
    }

    @Test
    fun signInWithGoogle_sinRed_devuelveNetwork() = runTest {
        transport.enqueue(failure(TransportFailureKind.UNREACHABLE))

        val outcome = failedGoogle(repository().signInWithGoogle(googleCredential()))

        assertTrue(outcome.error is DomainError.Network)
    }

    @Test
    fun signInWithGoogle_con500_devuelveServer() = runTest {
        transport.enqueue(response(500, ""))

        val outcome = failedGoogle(repository().signInWithGoogle(googleCredential()))

        assertTrue(outcome.error is DomainError.Server)
    }

    @Test
    fun signInWithGoogle_conEndpointInexistente_esNotFoundYNoIniciaSesion() = runTest {
        val backend = FakeAuthBackend(b8 = true)
        backend.googleEndpoint = false
        val repository = backendRepository(backend)

        val outcome = failedGoogle(repository.signInWithGoogle(googleCredential(FakeAuthBackend.googleIdToken("x@example.com"))))

        assertTrue(outcome.error is DomainError.NotFound)
        assertEquals(SessionState.SignedOut(), repository.sessionState.value)
    }

    @Test
    fun signInWithGoogle_conBackendSinB8_noIniciaSesion() = runTest {
        val backend = FakeAuthBackend(b8 = false)
        val repository = backendRepository(backend)

        val outcome = failedGoogle(repository.signInWithGoogle(googleCredential(FakeAuthBackend.googleIdToken("x@example.com"))))

        assertEquals("MOBILE_CLIENT_NOT_SUPPORTED", outcome.error.code)
        assertFalse(session.hasStoredSession())
    }

    @Test
    fun signInWithGoogle_cuentaLocalSinVerificar_noSeVincula() = runTest {
        val backend = FakeAuthBackend(b8 = true)
        backend.unverifiedLocalEmails += "luis@example.com"
        val repository = backendRepository(backend)

        val outcome = failedGoogle(repository.signInWithGoogle(googleCredential(FakeAuthBackend.googleIdToken("luis@example.com"))))

        assertEquals("LOCAL_ACCOUNT_EMAIL_NOT_VERIFIED", outcome.error.code)
        assertTrue(backend.linkedGoogleEmails.isEmpty())
        assertFalse(session.hasStoredSession())
        assertEquals(SessionState.SignedOut(), repository.sessionState.value)
    }

    @Test
    fun signInWithGoogle_cuentaLocalVerificada_seVincula() = runTest {
        val backend = FakeAuthBackend(b8 = true)
        val repository = backendRepository(backend)

        val outcome = repository.signInWithGoogle(googleCredential(FakeAuthBackend.googleIdToken(FakeAuthBackend.EMAIL)))

        assertEquals(setOf(FakeAuthBackend.EMAIL), backend.linkedGoogleEmails)
        assertNull((outcome as GoogleSignInOutcome.SignedIn).newAccountCodes)
        assertEquals(true, outcome.account.hasPassword)
        assertEquals("refresh-google", store.value)
    }

    @Test
    fun signInWithGoogle_sinCuenta_creaConCodigos() = runTest {
        val backend = FakeAuthBackend(b8 = true)
        val repository = backendRepository(backend)

        val outcome = repository.signInWithGoogle(googleCredential(FakeAuthBackend.googleIdToken("nuevo@example.com")))

        assertEquals(FakeAuthBackend.REGISTER_CODES, (outcome as GoogleSignInOutcome.SignedIn).newAccountCodes?.values)
        assertEquals(false, outcome.account.hasPassword)
        assertEquals(setOf("nuevo@example.com"), backend.googleOnlyEmails)
    }

    @Test
    fun signInWithGoogle_segundoAcceso_entraSinCodigos() = runTest {
        val backend = FakeAuthBackend(b8 = true)
        val first = backendRepository(backend)
        first.signInWithGoogle(googleCredential(FakeAuthBackend.googleIdToken("nuevo@example.com")))
        first.logout()

        val outcome = first.signInWithGoogle(googleCredential(FakeAuthBackend.googleIdToken("nuevo@example.com")))

        assertNull((outcome as GoogleSignInOutcome.SignedIn).newAccountCodes)
    }

    @Test
    fun login_conCuentaSoloGoogle_noIniciaSesion() = runTest {
        val backend = FakeAuthBackend(b8 = true)
        backend.googleOnlyEmails += "nuevo@example.com"
        val repository = backendRepository(backend)

        val outcome = failed(repository.login("nuevo@example.com", password()))

        assertTrue(outcome.error is DomainError.Unauthenticated)
        assertFalse(session.hasStoredSession())
        assertEquals(SessionState.SignedOut(), repository.sessionState.value)
    }

    @Test
    fun login_conRechazoDeCuentaSinContrasena_noIniciaSesion() = runTest {
        val backend = FakeAuthBackend(b8 = true)
        backend.googleOnlyEmails += "nuevo@example.com"
        backend.googleOnlyLoginRejection = "NO_LOCAL_PASSWORD"
        val repository = backendRepository(backend)

        val outcome = failed(repository.login("nuevo@example.com", password()))

        assertEquals("NO_LOCAL_PASSWORD", outcome.error.code)
        assertFalse(session.hasStoredSession())
        assertEquals(SessionState.SignedOut(), repository.sessionState.value)
    }

    @Test
    fun signInWithGoogle_canceladoTrasLaRespuesta_terminaDeIniciarLaSesion() = runTest {
        val repository = repository()
        lateinit var job: Job
        transport.enqueue { _ ->
            job.cancel()
            response(200, googleJson())
        }
        job = launch { repository.signInWithGoogle(googleCredential()) }

        advanceUntilIdle()
        job.join()

        assertEquals("r1", store.value)
        assertEquals(SessionState.SignedIn(account, offline = false), repository.sessionState.value)
    }
}
