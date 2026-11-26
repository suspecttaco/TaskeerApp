package com.simposio.taskeer.ui.accountsecurity

enum class ChangePasswordError {
    CURRENT_INVALID,
    NEW_INVALID,
    MISMATCH,
    CURRENT_INCORRECT,
    NEEDS_SIGN_IN,
    NO_LOCAL_PASSWORD,
    CONCURRENT_CHANGE,
    CHECK_DATA,
    RATE_LIMITED,
    NETWORK,
    SERVER,
    UNEXPECTED,
}

enum class ChangePasswordDone { CHANGED, SIGNED_OUT }

// Sin contraseñas: viven solo en los campos de la pantalla y en la petición. `secretsWiped` avisa de que hay que vaciarlos.
data class ChangePasswordUiState(
    val isSubmitting: Boolean = false,
    val error: ChangePasswordError? = null,
    val retryMinutes: Int? = null,
    val blocked: Boolean = false,
    val done: ChangePasswordDone? = null,
    val secretsWiped: Int = 0,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && !blocked && done == null
}
