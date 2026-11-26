package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.AuthRepository
import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.LoginOutcome
import com.simposio.taskeer.domain.auth.LogoutOutcome
import com.simposio.taskeer.domain.auth.RecoveryCodes
import com.simposio.taskeer.domain.auth.RegisterOutcome
import com.simposio.taskeer.domain.auth.SessionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAuthRepository(initial: SessionState = SessionState.SignedOut()) : AuthRepository {
    val state = MutableStateFlow(initial)
    override val sessionState: StateFlow<SessionState> = state

    var loginResult: LoginOutcome = LoginOutcome.SignedIn(AccountSummary("ana@example.com", "Ana"))
    var logoutResult = LogoutOutcome(serverRevoked = true)
    var registerResult: RegisterOutcome = RegisterOutcome.Registered(
        AccountSummary("ana@example.com", "Ana"),
        RecoveryCodes(List(RecoveryCodes.EXPECTED_COUNT) { "CODE-%04d".format(it) }),
    )
    var registerGate: CompletableDeferred<Unit>? = null
    var registerCalls = 0
    val registerNames = mutableListOf<String>()
    val registerEmails = mutableListOf<String>()
    var lastRegisterPassword: CharArray? = null
    var registerPasswordAtCall: String? = null
    var loginGate: CompletableDeferred<Unit>? = null
    var logoutGate: CompletableDeferred<Unit>? = null
    var resumeCalls = 0
    var logoutCalls = 0
    var acknowledgeCalls = 0
    val loginEmails = mutableListOf<String>()
    var passwordAtCall: String? = null
    var lastPasswordArray: CharArray? = null

    override suspend fun resume() {
        resumeCalls++
    }

    override suspend fun login(email: String, password: CharArray): LoginOutcome {
        loginEmails += email
        passwordAtCall = String(password)
        lastPasswordArray = password
        loginGate?.await()
        return loginResult
    }

    override suspend fun register(displayName: String, email: String, password: CharArray): RegisterOutcome {
        registerCalls++
        registerNames += displayName
        registerEmails += email
        registerPasswordAtCall = String(password)
        lastRegisterPassword = password
        registerGate?.await()
        return registerResult
    }

    override suspend fun logout(): LogoutOutcome {
        logoutCalls++
        logoutGate?.await()
        return logoutResult
    }

    override fun acknowledgeExpiration() {
        acknowledgeCalls++
    }
}
