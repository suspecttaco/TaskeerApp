package com.simposio.taskeer.data.remote.auth

import com.simposio.taskeer.data.remote.ApiClient
import com.simposio.taskeer.data.remote.ApiErrorMapper
import com.simposio.taskeer.data.remote.ApiRequest
import com.simposio.taskeer.data.remote.ApiResult
import com.simposio.taskeer.data.remote.AuthMode
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.remote.session.AuthSession
import com.simposio.taskeer.data.remote.session.RenewOutcome
import com.simposio.taskeer.data.repository.AuthRepository
import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.auth.LoginOutcome
import com.simposio.taskeer.domain.auth.LogoutOutcome
import com.simposio.taskeer.domain.auth.RecoveryCodes
import com.simposio.taskeer.domain.auth.RegisterOutcome
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RemoteAuthRepository(
    private val client: ApiClient,
    private val session: AuthSession,
    scope: CoroutineScope,
) : AuthRepository {

    private val authMutex = Mutex()
    private val state = MutableStateFlow<SessionState>(SessionState.SignedOut())

    // Cada logout lo incrementa: una reanudación que lo vea cambiado no debe resucitar la sesión.
    private val logoutEpoch = AtomicInteger()
    private val pendingRenewal = AtomicReference<Job?>(null)

    @Volatile
    private var knownAccount: AccountSummary? = null

    override val sessionState: StateFlow<SessionState> = state.asStateFlow()

    init {
        // `expirations` no tiene replay: hay que estar suscrito antes de cualquier renovación.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            session.expirations.collect { markExpired() }
        }
    }

    override suspend fun resume() {
        authMutex.withLock {
            val current = state.value
            if (current is SessionState.SignedIn && !current.offline) return
            if (!session.hasStoredSession()) {
                state.update { if (it is SessionState.SignedOut) it else SessionState.SignedOut() }
                return
            }
            val startEpoch = logoutEpoch.get()
            state.value = SessionState.Resuming
            when (renewForResume()) {
                null -> Unit
                is RenewOutcome.Renewed -> publish(startEpoch, SessionState.SignedIn(knownAccount, offline = false))
                RenewOutcome.SessionExpired -> markExpired(startEpoch)
                is RenewOutcome.Failed -> publish(startEpoch, SessionState.SignedIn(knownAccount, offline = true))
            }
        }
    }

    override suspend fun login(email: String, password: CharArray): LoginOutcome {
        try {
            AuthRules.validateCredentials(email, password)?.let { return LoginOutcome.Failed(it) }
            return authMutex.withLock {
                if (state.value is SessionState.SignedIn) {
                    return@withLock LoginOutcome.Failed(
                        DomainError.Validation(AuthRules.ALREADY_SIGNED_IN, "Ya hay una sesión iniciada"),
                    )
                }
                val request = ApiRequest(
                    method = HttpMethod.POST,
                    path = AuthApiContract.LOGIN_PATH,
                    jsonBody = AuthApiContract.loginBody(AuthRules.normalizedEmail(email), password),
                    auth = AuthMode.NONE,
                )
                when (val result = client.execute(request)) {
                    is ApiResult.Failure -> LoginOutcome.Failed(result.error, result.retryAfterSeconds)
                    is ApiResult.Success -> completeLogin(result.response.body)
                }
            }
        } finally {
            password.fill('\u0000')
        }
    }

    private suspend fun completeLogin(body: String): LoginOutcome =
        when (val parsed = AuthApiContract.parseLogin(body)) {
            null -> LoginOutcome.Failed(
                DomainError.Server("INVALID_LOGIN_RESPONSE", "Respuesta de inicio de sesión no válida del servidor"),
            )
            AuthApiContract.LoginResponse.NoRefreshToken -> LoginOutcome.Failed(
                DomainError.Forbidden(AuthRules.MOBILE_CLIENT_NOT_SUPPORTED, ApiErrorMapper.MOBILE_CLIENT_MESSAGE),
            )
            // Salir de la pantalla a mitad no debe dejar un refresh emitido por el servidor sin guardar.
            is AuthApiContract.LoginResponse.Tokens -> withContext(NonCancellable) {
                session.start(parsed.accessToken, parsed.refreshToken)
                knownAccount = parsed.account
                state.value = SessionState.SignedIn(parsed.account, offline = false)
                LoginOutcome.SignedIn(parsed.account)
            }
        }

    // No toma `authMutex` ni toca `AuthSession`: registrar no inicia sesión y no debe esperar a un login en vuelo.
    override suspend fun register(displayName: String, email: String, password: CharArray): RegisterOutcome {
        try {
            AuthRules.validateRegistration(displayName, email, password)?.let { return RegisterOutcome.Failed(it) }
            if (state.value is SessionState.SignedIn) {
                return RegisterOutcome.Failed(
                    DomainError.Validation(AuthRules.ALREADY_SIGNED_IN, "Ya hay una sesión iniciada"),
                )
            }
            val sentName = displayName.trim()
            val sentEmail = AuthRules.normalizedEmail(email)
            val request = ApiRequest(
                method = HttpMethod.POST,
                path = AuthApiContract.REGISTER_PATH,
                jsonBody = AuthApiContract.registerBody(sentName, sentEmail, password),
                auth = AuthMode.NONE,
            )
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> RegisterOutcome.Failed(result.error, result.retryAfterSeconds)
                is ApiResult.Success -> when (val parsed = AuthApiContract.parseRegister(result.response.body)) {
                    AuthApiContract.RegisterResponse.Unreadable -> RegisterOutcome.RegisteredWithoutCodes(sentEmail)
                    is AuthApiContract.RegisterResponse.Created ->
                        if (parsed.codes.isEmpty()) {
                            RegisterOutcome.RegisteredWithoutCodes(sentEmail)
                        } else {
                            RegisterOutcome.Registered(
                                AccountSummary(
                                    email = parsed.account?.email?.takeIf { it.isNotBlank() } ?: sentEmail,
                                    displayName = parsed.account?.displayName?.takeIf { it.isNotBlank() } ?: sentName,
                                ),
                                RecoveryCodes(parsed.codes),
                            )
                        }
                }
            }
        } finally {
            password.fill('\u0000')
        }
    }

    override suspend fun logout(): LogoutOutcome {
        logoutEpoch.incrementAndGet()
        pendingRenewal.get()?.cancel()
        val refreshToken = session.endSession()
        knownAccount = null
        state.value = SessionState.SignedOut()
        if (refreshToken == null) return LogoutOutcome(serverRevoked = true)
        return withContext(NonCancellable) {
            val result = client.execute(
                ApiRequest(
                    method = HttpMethod.POST,
                    path = AuthApiContract.LOGOUT_PATH,
                    jsonBody = AuthApiContract.logoutBody(refreshToken),
                    auth = AuthMode.NONE,
                ),
            )
            // 401 equivale a revocado: el servidor ya no reconocía ese token.
            LogoutOutcome(
                serverRevoked = result is ApiResult.Success ||
                    (result is ApiResult.Failure && result.error is DomainError.Unauthenticated),
            )
        }
    }

    override fun acknowledgeExpiration() {
        state.update { if (it is SessionState.SignedOut && it.expired) SessionState.SignedOut() else it }
    }

    private suspend fun renewForResume(): RenewOutcome? = coroutineScope {
        val renewal = async { session.renew(null) }
        pendingRenewal.set(renewal)
        try {
            renewal.await()
        } catch (e: CancellationException) {
            if (renewal.isCancelled && isActive) null else throw e
        } finally {
            pendingRenewal.set(null)
        }
    }

    private fun publish(startEpoch: Int, next: SessionState) {
        state.update { if (logoutEpoch.get() != startEpoch) SessionState.SignedOut() else next }
    }

    private fun markExpired(startEpoch: Int? = null) {
        knownAccount = null
        state.update {
            when {
                startEpoch != null && logoutEpoch.get() != startEpoch -> SessionState.SignedOut()
                it is SessionState.SignedIn || it == SessionState.Resuming -> SessionState.SignedOut(expired = true)
                else -> it
            }
        }
    }
}
