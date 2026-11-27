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
import com.simposio.taskeer.domain.auth.ChangePasswordOutcome
import com.simposio.taskeer.domain.auth.GoogleCredential
import com.simposio.taskeer.domain.auth.GoogleSignInOutcome
import com.simposio.taskeer.domain.auth.LoginOutcome
import com.simposio.taskeer.domain.auth.LogoutOutcome
import com.simposio.taskeer.domain.auth.RecoveryCodeInput
import com.simposio.taskeer.domain.auth.RecoveryCodes
import com.simposio.taskeer.domain.auth.RegenerateCodesOutcome
import com.simposio.taskeer.domain.auth.RegisterOutcome
import com.simposio.taskeer.domain.auth.ResendVerificationOutcome
import com.simposio.taskeer.domain.auth.ResetPasswordOutcome
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.auth.VerificationInput
import com.simposio.taskeer.domain.auth.VerifyEmailOutcome
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
    private val googleExchange: GoogleTokenExchange? = null,
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
                startSession(parsed.accessToken, parsed.refreshToken, parsed.account)
                LoginOutcome.SignedIn(parsed.account)
            }
        }

    private suspend fun startSession(accessToken: String, refreshToken: String, account: AccountSummary) {
        session.start(accessToken, refreshToken)
        knownAccount = account
        state.value = SessionState.SignedIn(account, offline = false)
    }

    override suspend fun signInWithGoogle(credential: GoogleCredential): GoogleSignInOutcome =
        authMutex.withLock {
            if (state.value is SessionState.SignedIn) {
                return@withLock GoogleSignInOutcome.Failed(
                    DomainError.Validation(AuthRules.ALREADY_SIGNED_IN, "Ya hay una sesión iniciada"),
                )
            }
            val exchange = googleExchange ?: return@withLock GoogleSignInOutcome.Failed(
                DomainError.Server("GOOGLE_EXCHANGE_NOT_CONFIGURED", "El acceso con Google no está configurado"),
            )
            // El token de Supabase solo vive en esta variable local: no se guarda ni se reintenta.
            val supabaseToken = when (val exchanged = exchange.exchange(credential)) {
                is GoogleExchangeResult.Failed ->
                    return@withLock GoogleSignInOutcome.Failed(exchanged.error, exchanged.retryAfterSeconds)
                is GoogleExchangeResult.Token -> exchanged.accessToken
            }
            val request = ApiRequest(
                method = HttpMethod.POST,
                path = AuthApiContract.GOOGLE_PATH,
                jsonBody = AuthApiContract.supabaseBody(supabaseToken),
                auth = AuthMode.NONE,
            )
            when (val result = client.execute(request)) {
                is ApiResult.Failure -> GoogleSignInOutcome.Failed(result.error, result.retryAfterSeconds)
                is ApiResult.Success -> completeGoogleSignIn(result.response.body)
            }
        }

    private suspend fun completeGoogleSignIn(body: String): GoogleSignInOutcome =
        when (val parsed = AuthApiContract.parseGoogle(body)) {
            AuthApiContract.GoogleResponse.Unreadable -> GoogleSignInOutcome.Failed(
                DomainError.Server("INVALID_LOGIN_RESPONSE", "Respuesta de inicio de sesión no válida del servidor"),
            )
            AuthApiContract.GoogleResponse.NoRefreshToken -> GoogleSignInOutcome.Failed(
                DomainError.Forbidden(AuthRules.MOBILE_CLIENT_NOT_SUPPORTED, ApiErrorMapper.MOBILE_CLIENT_MESSAGE),
            )
            // Salir de la pantalla a mitad no debe dejar un refresh emitido por el servidor sin guardar.
            is AuthApiContract.GoogleResponse.Tokens -> withContext(NonCancellable) {
                startSession(parsed.accessToken, parsed.refreshToken, parsed.account)
                GoogleSignInOutcome.SignedIn(parsed.account, parsed.codes.takeIf { it.isNotEmpty() }?.let(::RecoveryCodes))
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
                                    emailVerified = parsed.account?.emailVerified,
                                    hasPassword = parsed.account?.hasPassword,
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

    // No toma `authMutex` ni toca `AuthSession`: el endpoint es público y verificar no cambia la sesión.
    override suspend fun verifyEmail(token: String): VerifyEmailOutcome {
        val parsed = VerificationInput.parseToken(token)
            ?: return VerifyEmailOutcome.Failed(DomainError.Validation(VerificationInput.TOKEN_INVALID, "Token no válido"))
        val request = ApiRequest(
            method = HttpMethod.GET,
            path = AuthApiContract.VERIFY_EMAIL_PATH,
            query = listOf("token" to parsed),
            auth = AuthMode.NONE,
        )
        return when (val result = client.execute(request)) {
            is ApiResult.Failure -> VerifyEmailOutcome.Failed(result.error, result.retryAfterSeconds)
            is ApiResult.Success ->
                if (AuthApiContract.parseVerified(result.response.body)) {
                    VerifyEmailOutcome.Verified
                } else {
                    VerifyEmailOutcome.Failed(DomainError.Server("VERIFY_UNREADABLE", UNREADABLE_MESSAGE))
                }
        }
    }

    override suspend fun resendVerification(): ResendVerificationOutcome {
        if (state.value !is SessionState.SignedIn) {
            return ResendVerificationOutcome.Failed(
                DomainError.Unauthenticated(NOT_SIGNED_IN, "No hay una sesión iniciada"),
            )
        }
        val request = ApiRequest(
            method = HttpMethod.POST,
            path = AuthApiContract.RESEND_VERIFICATION_PATH,
            auth = AuthMode.BEARER,
        )
        return when (val result = client.execute(request)) {
            is ApiResult.Failure -> ResendVerificationOutcome.Failed(result.error, result.retryAfterSeconds)
            is ApiResult.Success -> when (AuthApiContract.parseAlreadyVerified(result.response.body)) {
                true -> ResendVerificationOutcome.AlreadyVerified
                false -> ResendVerificationOutcome.Sent
                null -> ResendVerificationOutcome.Failed(
                    DomainError.Server("RESEND_UNREADABLE", UNREADABLE_MESSAGE),
                )
            }
        }
    }

    // No toma `authMutex` ni toca `AuthSession`: el reset es público y no inicia ni cierra sesión.
    override suspend fun resetPassword(email: String, code: String, newPassword: CharArray): ResetPasswordOutcome {
        try {
            if (!AuthRules.isEmailShapeValid(email)) {
                return ResetPasswordOutcome.Failed(DomainError.Validation(AuthRules.EMAIL_INVALID, "Escribe un correo válido"))
            }
            val normalizedCode = RecoveryCodeInput.normalize(code)
                ?: return ResetPasswordOutcome.Failed(
                    DomainError.Validation(AuthRules.CODE_INVALID, "El código tiene el formato XXXX-XXXX"),
                )
            if (newPassword.size < AuthRules.PASSWORD_MIN || newPassword.size > AuthRules.PASSWORD_MAX) {
                return ResetPasswordOutcome.Failed(
                    DomainError.Validation(AuthRules.PASSWORD_TOO_SHORT, "La contraseña debe tener entre 8 y 128 caracteres"),
                )
            }
            val request = ApiRequest(
                method = HttpMethod.POST,
                path = AuthApiContract.RESET_PASSWORD_PATH,
                jsonBody = AuthApiContract.resetBody(AuthRules.normalizedEmail(email), normalizedCode, newPassword),
                auth = AuthMode.NONE,
            )
            // Cualquier 2xx es éxito: el servidor ya gastó el código y reintentar con él fallaría.
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> ResetPasswordOutcome.Failed(result.error, result.retryAfterSeconds)
                is ApiResult.Success -> ResetPasswordOutcome.Reset
            }
        } finally {
            newPassword.fill('\u0000')
        }
    }

    override suspend fun changePassword(currentPassword: CharArray, newPassword: CharArray): ChangePasswordOutcome {
        try {
            AuthRules.validateCurrentPassword(currentPassword)?.let { return ChangePasswordOutcome.Failed(it) }
            if (newPassword.size < AuthRules.PASSWORD_MIN || newPassword.size > AuthRules.PASSWORD_MAX) {
                return ChangePasswordOutcome.Failed(
                    DomainError.Validation(AuthRules.PASSWORD_TOO_SHORT, "La contraseña debe tener entre 8 y 128 caracteres"),
                )
            }
            return authMutex.withLock {
                if (state.value !is SessionState.SignedIn) {
                    return@withLock ChangePasswordOutcome.Failed(
                        DomainError.Unauthenticated(NOT_SIGNED_IN, "No hay una sesión iniciada"),
                    )
                }
                val request = ApiRequest(
                    method = HttpMethod.POST,
                    path = AuthApiContract.CHANGE_PASSWORD_PATH,
                    jsonBody = AuthApiContract.changePasswordBody(currentPassword, newPassword),
                    auth = AuthMode.BEARER,
                )
                when (val result = client.execute(request)) {
                    is ApiResult.Failure -> ChangePasswordOutcome.Failed(result.error, result.retryAfterSeconds)
                    // Salir de la pantalla a mitad no debe dejar un refresh emitido por el servidor sin guardar.
                    is ApiResult.Success -> withContext(NonCancellable) { completePasswordChange(result.response.body) }
                }
            }
        } finally {
            currentPassword.fill('\u0000')
            newPassword.fill('\u0000')
        }
    }

    private suspend fun completePasswordChange(body: String): ChangePasswordOutcome =
        when (val parsed = AuthApiContract.parseLogin(body)) {
            is AuthApiContract.LoginResponse.Tokens -> {
                session.start(parsed.accessToken, parsed.refreshToken)
                knownAccount = parsed.account
                state.value = SessionState.SignedIn(parsed.account, offline = false)
                ChangePasswordOutcome.Changed(parsed.account)
            }
            // El servidor ya revocó el refresh local: mantener SignedIn solo produciría un 401 confuso a los 15 min.
            AuthApiContract.LoginResponse.NoRefreshToken, null -> {
                logoutEpoch.incrementAndGet()
                pendingRenewal.get()?.cancel()
                session.endSession()
                knownAccount = null
                state.value = SessionState.SignedOut()
                ChangePasswordOutcome.ChangedSignedOut
            }
        }

    // No toma `authMutex`: regenerar no cambia la sesión.
    override suspend fun regenerateRecoveryCodes(currentPassword: CharArray): RegenerateCodesOutcome {
        try {
            AuthRules.validateCurrentPassword(currentPassword)?.let { return RegenerateCodesOutcome.Failed(it) }
            if (state.value !is SessionState.SignedIn) {
                return RegenerateCodesOutcome.Failed(
                    DomainError.Unauthenticated(NOT_SIGNED_IN, "No hay una sesión iniciada"),
                )
            }
            val request = ApiRequest(
                method = HttpMethod.POST,
                path = AuthApiContract.REGENERATE_CODES_PATH,
                jsonBody = AuthApiContract.regenerateBody(currentPassword),
                auth = AuthMode.BEARER,
            )
            return when (val result = client.execute(request)) {
                is ApiResult.Failure -> RegenerateCodesOutcome.Failed(result.error, result.retryAfterSeconds)
                is ApiResult.Success -> AuthApiContract.parseRecoveryCodes(result.response.body)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { RegenerateCodesOutcome.Regenerated(RecoveryCodes(it)) }
                    ?: RegenerateCodesOutcome.RegeneratedWithoutCodes
            }
        } finally {
            currentPassword.fill('\u0000')
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

    private companion object {
        const val NOT_SIGNED_IN = "NOT_SIGNED_IN"
        const val UNREADABLE_MESSAGE = "Respuesta no válida del servidor"
    }
}
