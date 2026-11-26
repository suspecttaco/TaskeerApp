package com.simposio.taskeer.ui.verifyemail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.AuthRepository
import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.auth.ResendVerificationOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ResendMessage {
    SENT,
    ALREADY_VERIFIED,
    DISABLED,
    NEEDS_SIGN_IN,
    RATE_LIMITED,
    NETWORK,
    SERVER,
    NOT_SUPPORTED,
    UNEXPECTED,
}

data class ResendVerificationUiState(
    val isSending: Boolean = false,
    val message: ResendMessage? = null,
    val retryMinutes: Int? = null,
    val blocked: Boolean = false,
) {
    val canResend: Boolean
        get() = !isSending && !blocked
}

class ResendVerificationViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ResendVerificationUiState())
    val uiState: StateFlow<ResendVerificationUiState> = _uiState.asStateFlow()

    fun resend() {
        if (!_uiState.value.canResend) return
        _uiState.update { it.copy(isSending = true, message = null, retryMinutes = null) }
        viewModelScope.launch {
            try {
                apply(auth.resendVerification())
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null, retryMinutes = null) }
    }

    private fun apply(outcome: ResendVerificationOutcome) {
        when (outcome) {
            ResendVerificationOutcome.Sent -> _uiState.update { it.copy(message = ResendMessage.SENT) }
            ResendVerificationOutcome.AlreadyVerified ->
                _uiState.update { it.copy(message = ResendMessage.ALREADY_VERIFIED) }
            is ResendVerificationOutcome.Failed ->
                applyFailure(verifyEmailErrorFor(outcome.error, VerifyEmailOperation.RESEND), outcome.retryAfterSeconds)
        }
    }

    private fun applyFailure(error: VerifyEmailError, retryAfterSeconds: Long?) {
        val rateLimited = error == VerifyEmailError.RATE_LIMITED
        val lockoutSeconds = if (rateLimited) AuthRules.lockoutSeconds(retryAfterSeconds) else 0L
        _uiState.update {
            it.copy(
                message = messageFor(error),
                retryMinutes = if (rateLimited) AuthRules.retryMinutes(retryAfterSeconds) else null,
                blocked = lockoutSeconds > 0 || it.blocked,
            )
        }
        if (lockoutSeconds > 0) {
            viewModelScope.launch {
                delay(lockoutSeconds * MILLIS_PER_SECOND)
                _uiState.update { it.copy(blocked = false) }
            }
        }
    }

    private fun messageFor(error: VerifyEmailError): ResendMessage = when (error) {
        VerifyEmailError.DISABLED -> ResendMessage.DISABLED
        VerifyEmailError.NEEDS_SIGN_IN -> ResendMessage.NEEDS_SIGN_IN
        VerifyEmailError.RATE_LIMITED -> ResendMessage.RATE_LIMITED
        VerifyEmailError.NETWORK -> ResendMessage.NETWORK
        VerifyEmailError.SERVER -> ResendMessage.SERVER
        VerifyEmailError.NOT_SUPPORTED -> ResendMessage.NOT_SUPPORTED
        VerifyEmailError.INPUT_INVALID,
        VerifyEmailError.INVALID_OR_EXPIRED,
        VerifyEmailError.UNEXPECTED,
        -> ResendMessage.UNEXPECTED
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
