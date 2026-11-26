package com.simposio.taskeer.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.AuthRepository
import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.auth.LoginOutcome
import com.simposio.taskeer.domain.auth.SessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        // La pantalla solo es alcanzable sin sesión: si aparece una mientras está abierta, se cierra.
        viewModelScope.launch {
            auth.sessionState.collect { session ->
                if (session is SessionState.SignedIn) _uiState.update { it.copy(signedIn = true) }
            }
        }
    }

    fun prefill(email: String) {
        _uiState.update { it.copy(email = email, accountCreated = true) }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, error = null, retryMinutes = null, accountCreated = false) }
    }

    fun onPasswordEdited() {
        _uiState.update {
            if (it.error == null && !it.accountCreated) {
                it
            } else {
                it.copy(error = null, retryMinutes = null, accountCreated = false)
            }
        }
    }

    fun dismissNotSupported() {
        _uiState.update { if (it.blocked == LoginBlock.NOT_SUPPORTED) it.copy(blocked = null) else it }
    }

    fun submit(email: String, password: CharArray) {
        val current = _uiState.value
        if (!current.canSubmit) {
            password.fill(ZERO)
            return
        }
        val invalid = AuthRules.validateCredentials(email, password)
        if (invalid != null) {
            password.fill(ZERO)
            val failure = loginFailureFor(invalid) as LoginFailure.Inline
            _uiState.update {
                it.copy(email = email, error = failure.error, retryMinutes = null, accountCreated = false)
            }
            return
        }
        _uiState.update {
            it.copy(email = email, error = null, retryMinutes = null, isSubmitting = true, accountCreated = false)
        }
        viewModelScope.launch {
            try {
                apply(auth.login(email, password))
            } finally {
                password.fill(ZERO)
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    private fun apply(outcome: LoginOutcome) {
        when (outcome) {
            is LoginOutcome.SignedIn ->
                _uiState.update { it.copy(signedIn = true, passwordResetCount = it.passwordResetCount + 1) }
            is LoginOutcome.Failed -> when (val failure = loginFailureFor(outcome.error)) {
                LoginFailure.NotSupported -> _uiState.update { it.copy(blocked = LoginBlock.NOT_SUPPORTED) }
                is LoginFailure.Inline -> applyInline(failure.error, outcome.retryAfterSeconds)
            }
        }
    }

    private fun applyInline(error: LoginError, retryAfterSeconds: Long?) {
        val lockoutSeconds = if (error == LoginError.RATE_LIMITED) AuthRules.lockoutSeconds(retryAfterSeconds) else 0L
        _uiState.update {
            it.copy(
                error = error,
                retryMinutes = if (error == LoginError.RATE_LIMITED) AuthRules.retryMinutes(retryAfterSeconds) else null,
                blocked = if (lockoutSeconds > 0) LoginBlock.RATE_LIMITED else it.blocked,
                passwordResetCount = it.passwordResetCount + if (error == LoginError.INVALID_CREDENTIALS) 1 else 0,
            )
        }
        if (lockoutSeconds > 0) {
            viewModelScope.launch {
                delay(lockoutSeconds * MILLIS_PER_SECOND)
                _uiState.update { if (it.blocked == LoginBlock.RATE_LIMITED) it.copy(blocked = null) else it }
            }
        }
    }

    private companion object {
        const val ZERO = '\u0000'
        const val MILLIS_PER_SECOND = 1_000L
    }
}
