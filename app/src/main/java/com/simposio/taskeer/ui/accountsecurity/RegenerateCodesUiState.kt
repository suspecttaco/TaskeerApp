package com.simposio.taskeer.ui.accountsecurity

enum class RegenerateCodesPhase { PASSWORD, CODES, CODES_LOST, NO_CODES, FINISHED }

enum class RegenerateCodesError {
    CURRENT_INVALID,
    CURRENT_INCORRECT,
    NEEDS_SIGN_IN,
    NO_LOCAL_PASSWORD,
    CHECK_DATA,
    RATE_LIMITED,
    NETWORK,
    SERVER,
    UNEXPECTED,
}

// Sin contraseña ni códigos: los códigos viven solo en `RegenerateCodesViewModel.recoveryCodes`.
data class RegenerateCodesUiState(
    val phase: RegenerateCodesPhase = RegenerateCodesPhase.PASSWORD,
    val isSubmitting: Boolean = false,
    val error: RegenerateCodesError? = null,
    val retryMinutes: Int? = null,
    val blocked: Boolean = false,
    val savedChecked: Boolean = false,
    val copied: Boolean = false,
    val secretsWiped: Int = 0,
) {
    val canSubmit: Boolean
        get() = phase == RegenerateCodesPhase.PASSWORD && !isSubmitting && !blocked

    val canContinue: Boolean
        get() = phase == RegenerateCodesPhase.CODES && savedChecked
}
