package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.AuthRepository
import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.ChangePasswordOutcome
import com.simposio.taskeer.domain.auth.LoginOutcome
import com.simposio.taskeer.domain.auth.LogoutOutcome
import com.simposio.taskeer.domain.auth.RecoveryCodes
import com.simposio.taskeer.domain.auth.RegenerateCodesOutcome
import com.simposio.taskeer.domain.auth.RegisterOutcome
import com.simposio.taskeer.domain.auth.ResendVerificationOutcome
import com.simposio.taskeer.domain.auth.ResetPasswordOutcome
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.auth.VerifyEmailOutcome
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
    var verifyResult: VerifyEmailOutcome = VerifyEmailOutcome.Verified
    var verifyGate: CompletableDeferred<Unit>? = null
    var verifyCalls = 0
    val verifyTokens = mutableListOf<String>()
    var resendResult: ResendVerificationOutcome = ResendVerificationOutcome.Sent
    var resendGate: CompletableDeferred<Unit>? = null
    var resendCalls = 0
    var resetResult: ResetPasswordOutcome = ResetPasswordOutcome.Reset
    var resetGate: CompletableDeferred<Unit>? = null
    var resetCalls = 0
    var lastCodeSent: String? = null
    val resetEmails = mutableListOf<String>()
    var lastResetPassword: CharArray? = null
    var changeResult: ChangePasswordOutcome = ChangePasswordOutcome.Changed(AccountSummary("ana@example.com", "Ana"))
    var changeGate: CompletableDeferred<Unit>? = null
    var changeCalls = 0
    var lastChangeCurrent: CharArray? = null
    var lastChangeNew: CharArray? = null
    var regenerateResult: RegenerateCodesOutcome = RegenerateCodesOutcome.Regenerated(
        RecoveryCodes(List(RecoveryCodes.EXPECTED_COUNT) { "NEW1-%04d".format(it) }),
    )
    var regenerateGate: CompletableDeferred<Unit>? = null
    var regenerateCalls = 0
    var lastRegenerateCurrent: CharArray? = null
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

    override suspend fun verifyEmail(token: String): VerifyEmailOutcome {
        verifyCalls++
        verifyTokens += token
        verifyGate?.await()
        return verifyResult
    }

    override suspend fun resendVerification(): ResendVerificationOutcome {
        resendCalls++
        resendGate?.await()
        return resendResult
    }

    override suspend fun resetPassword(email: String, code: String, newPassword: CharArray): ResetPasswordOutcome {
        resetCalls++
        resetEmails += email
        lastCodeSent = code
        lastResetPassword = newPassword
        resetGate?.await()
        return resetResult
    }

    override suspend fun changePassword(currentPassword: CharArray, newPassword: CharArray): ChangePasswordOutcome {
        changeCalls++
        lastChangeCurrent = currentPassword
        lastChangeNew = newPassword
        changeGate?.await()
        return changeResult
    }

    override suspend fun regenerateRecoveryCodes(currentPassword: CharArray): RegenerateCodesOutcome {
        regenerateCalls++
        lastRegenerateCurrent = currentPassword
        regenerateGate?.await()
        return regenerateResult
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
