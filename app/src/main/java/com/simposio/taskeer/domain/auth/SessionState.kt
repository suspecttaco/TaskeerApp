package com.simposio.taskeer.domain.auth

sealed interface SessionState {
    data class SignedOut(val expired: Boolean = false) : SessionState

    data object Resuming : SessionState

    data class SignedIn(val account: AccountSummary?, val offline: Boolean) : SessionState
}

// `emailVerified` y `hasPassword` nulos = desconocidos; solo llegan en login, registro y cambio de contraseña, no hay GET /me.
data class AccountSummary(
    val email: String,
    val displayName: String,
    val emailVerified: Boolean? = null,
    val hasPassword: Boolean? = null,
) {
    // `hasPassword` es un indicador, no un secreto; se omite para que ningún log ni aserción de redacción lo confunda con una contraseña.
    override fun toString(): String = "AccountSummary(email=$email, displayName=$displayName, emailVerified=$emailVerified)"
}
