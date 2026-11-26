package com.simposio.taskeer.domain.auth

import com.simposio.taskeer.domain.model.DomainError

sealed interface LoginOutcome {
    data class SignedIn(val account: AccountSummary) : LoginOutcome

    data class Failed(val error: DomainError, val retryAfterSeconds: Long? = null) : LoginOutcome
}

sealed interface GoogleSignInOutcome {
    // `newAccountCodes` solo llega si este acceso creó la cuenta; es el único momento en que existen.
    class SignedIn(val account: AccountSummary, val newAccountCodes: RecoveryCodes?) : GoogleSignInOutcome {
        override fun toString(): String = "SignedIn(account=$account, newAccountCodes=${newAccountCodes?.count})"
    }

    data class Failed(val error: DomainError, val retryAfterSeconds: Long? = null) : GoogleSignInOutcome
}

sealed interface RegisterOutcome {
    data class Registered(val account: AccountSummary, val recoveryCodes: RecoveryCodes) : RegisterOutcome

    // La cuenta existe en el servidor aunque no llegaran códigos: reintentar daría 409.
    data class RegisteredWithoutCodes(val email: String) : RegisterOutcome

    data class Failed(val error: DomainError, val retryAfterSeconds: Long? = null) : RegisterOutcome
}

sealed interface VerifyEmailOutcome {
    data object Verified : VerifyEmailOutcome

    data class Failed(val error: DomainError, val retryAfterSeconds: Long? = null) : VerifyEmailOutcome
}

sealed interface ResendVerificationOutcome {
    data object Sent : ResendVerificationOutcome

    data object AlreadyVerified : ResendVerificationOutcome

    data class Failed(val error: DomainError, val retryAfterSeconds: Long? = null) : ResendVerificationOutcome
}

sealed interface ResetPasswordOutcome {
    data object Reset : ResetPasswordOutcome

    data class Failed(val error: DomainError, val retryAfterSeconds: Long? = null) : ResetPasswordOutcome
}

sealed interface ChangePasswordOutcome {
    data class Changed(val account: AccountSummary) : ChangePasswordOutcome

    // La contraseña cambió pero la app no recibió el refresh nuevo: la sesión local se cierra.
    data object ChangedSignedOut : ChangePasswordOutcome

    data class Failed(val error: DomainError, val retryAfterSeconds: Long? = null) : ChangePasswordOutcome
}

sealed interface RegenerateCodesOutcome {
    data class Regenerated(val codes: RecoveryCodes) : RegenerateCodesOutcome

    // Los códigos anteriores ya no valen aunque no llegaran los nuevos.
    data object RegeneratedWithoutCodes : RegenerateCodesOutcome

    data class Failed(val error: DomainError, val retryAfterSeconds: Long? = null) : RegenerateCodesOutcome
}

data class LogoutOutcome(val serverRevoked: Boolean)
