package com.simposio.taskeer.ui.verifyemail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.AuthRepository
import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.auth.VerificationInput
import com.simposio.taskeer.domain.auth.VerifyEmailOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VerifyEmailViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(VerifyEmailUiState())
    val uiState: StateFlow<VerifyEmailUiState> = _uiState.asStateFlow()

    fun onInputEdited() {
        _uiState.update { if (it.error == null) it else it.copy(error = null, retryMinutes = null) }
    }

    fun submit(raw: String) {
        if (!_uiState.value.canSubmit) return
        val token = VerificationInput.parseToken(raw)
        if (token == null) {
            _uiState.update { it.copy(error = VerifyEmailError.INPUT_INVALID, retryMinutes = null) }
            return
        }
        _uiState.update { it.copy(phase = VerifyEmailPhase.VERIFYING, error = null, retryMinutes = null) }
        viewModelScope.launch { apply(auth.verifyEmail(token)) }
    }

    private fun apply(outcome: VerifyEmailOutcome) {
        when (outcome) {
            VerifyEmailOutcome.Verified -> _uiState.update { it.copy(phase = VerifyEmailPhase.VERIFIED) }
            is VerifyEmailOutcome.Failed ->
                applyFailure(verifyEmailErrorFor(outcome.error, VerifyEmailOperation.VERIFY), outcome.retryAfterSeconds)
        }
    }

    private fun applyFailure(error: VerifyEmailError, retryAfterSeconds: Long?) {
        val rateLimited = error == VerifyEmailError.RATE_LIMITED
        val lockoutSeconds = if (rateLimited) AuthRules.lockoutSeconds(retryAfterSeconds) else 0L
        _uiState.update {
            it.copy(
                phase = VerifyEmailPhase.INPUT,
                error = error,
                retryMinutes = if (rateLimited) AuthRules.retryMinutes(retryAfterSeconds) else null,
                blocked = if (lockoutSeconds > 0) VerifyEmailBlock.RATE_LIMITED else it.blocked,
            )
        }
        if (lockoutSeconds > 0) {
            viewModelScope.launch {
                delay(lockoutSeconds * MILLIS_PER_SECOND)
                _uiState.update { if (it.blocked == VerifyEmailBlock.RATE_LIMITED) it.copy(blocked = null) else it }
            }
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
