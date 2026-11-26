package com.simposio.taskeer.ui.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.auth.google.GoogleCredentialSource
import com.simposio.taskeer.data.repository.AuthRepository
import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.auth.GoogleCredentialResult
import com.simposio.taskeer.domain.auth.GoogleSignInConfig
import com.simposio.taskeer.domain.auth.GoogleSignInOutcome
import com.simposio.taskeer.domain.auth.LoginOutcome
import com.simposio.taskeer.domain.auth.RecoveryCodes
import com.simposio.taskeer.domain.auth.SessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val auth: AuthRepository,
    private val google: GoogleCredentialSource,
    googleConfig: GoogleSignInConfig,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LoginUiState(
            googleAvailable = googleConfig.isAvailable,
            phase = if (savedState.get<Boolean>(KEY_CODES_PENDING) == true) LoginPhase.CODES_LOST else LoginPhase.FORM,
        ),
    )
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Único lugar donde viven los códigos de una cuenta creada con Google: memoria, nunca en UiState ni en SavedStateHandle.
    private val _recoveryCodes = MutableStateFlow<RecoveryCodes?>(null)
    val recoveryCodes: StateFlow<RecoveryCodes?> = _recoveryCodes.asStateFlow()

    init {
        // La pantalla solo es alcanzable sin sesión: si aparece una mientras está abierta, se cierra. Mientras un acceso
        // con Google está en curso, o hay códigos por ver, decide `applyGoogle` cuándo salir.
        viewModelScope.launch {
            auth.sessionState.collect { session ->
                if (session is SessionState.SignedIn) {
                    _uiState.update {
                        if (it.googleInProgress || it.phase != LoginPhase.FORM) it else it.copy(signedIn = true)
                    }
                }
            }
        }
    }

    fun prefill(email: String, notice: LoginNotice = LoginNotice.ACCOUNT_CREATED) {
        _uiState.update { it.copy(email = email, notice = notice) }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, error = null, retryMinutes = null, notice = null) }
    }

    fun onPasswordEdited() {
        _uiState.update {
            if (it.error == null && it.notice == null) {
                it
            } else {
                it.copy(error = null, retryMinutes = null, notice = null)
            }
        }
    }

    fun dismissNotSupported() {
        _uiState.update { if (it.blocked == LoginBlock.NOT_SUPPORTED) it.copy(blocked = null) else it }
    }

    // `host` es la Activity que muestra la hoja de Google; solo viaja como argumento durante el intento.
    fun signInWithGoogle(host: Any) {
        val current = _uiState.value
        if (!current.canUseGoogle || current.phase != LoginPhase.FORM) return
        _uiState.update {
            it.copy(googleInProgress = true, error = null, retryMinutes = null, notice = null)
        }
        viewModelScope.launch {
            try {
                when (val result = google.obtain(host)) {
                    is GoogleCredentialResult.Obtained -> applyGoogle(auth.signInWithGoogle(result.credential))
                    else -> googleResultErrorFor(result)?.let { error -> _uiState.update { it.copy(error = error) } }
                }
            } finally {
                _uiState.update {
                    val enteredWhileBusy = it.phase == LoginPhase.FORM && auth.sessionState.value is SessionState.SignedIn
                    it.copy(googleInProgress = false, signedIn = it.signedIn || enteredWhileBusy)
                }
            }
        }
    }

    fun onSavedCheckedChange(checked: Boolean) {
        _uiState.update { if (it.phase == LoginPhase.CODES) it.copy(savedChecked = checked) else it }
    }

    fun onCodesCopied() {
        _uiState.update { if (it.phase == LoginPhase.CODES) it.copy(copied = true) else it }
    }

    fun confirmCodesSaved() {
        val current = _uiState.value
        if (current.phase == LoginPhase.CODES && current.savedChecked) discardCodesAndFinish()
    }

    fun leaveWithoutSaving() {
        if (_uiState.value.phase == LoginPhase.CODES) discardCodesAndFinish()
    }

    fun acknowledgeLostCodes() {
        if (_uiState.value.phase != LoginPhase.CODES_LOST) return
        savedState.remove<Boolean>(KEY_CODES_PENDING)
        _uiState.update {
            it.copy(phase = LoginPhase.FORM, signedIn = auth.sessionState.value is SessionState.SignedIn)
        }
    }

    public override fun onCleared() {
        _recoveryCodes.value = null
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
                it.copy(email = email, error = failure.error, retryMinutes = null, notice = null)
            }
            return
        }
        _uiState.update {
            it.copy(email = email, error = null, retryMinutes = null, isSubmitting = true, notice = null)
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

    private fun applyGoogle(outcome: GoogleSignInOutcome) {
        when (outcome) {
            is GoogleSignInOutcome.SignedIn -> {
                val codes = outcome.newAccountCodes
                if (codes == null) {
                    _uiState.update { it.copy(signedIn = true) }
                } else {
                    _recoveryCodes.value = codes
                    savedState[KEY_CODES_PENDING] = true
                    _uiState.update { it.copy(phase = LoginPhase.CODES, savedChecked = false, copied = false) }
                }
            }
            is GoogleSignInOutcome.Failed -> when (val failure = googleFailureFor(outcome.error)) {
                LoginFailure.NotSupported -> _uiState.update { it.copy(blocked = LoginBlock.NOT_SUPPORTED) }
                is LoginFailure.Inline -> applyInline(failure.error, outcome.retryAfterSeconds)
            }
        }
    }

    private fun discardCodesAndFinish() {
        // Se retira la fase antes que los códigos: `CODES` siempre implica códigos presentes.
        _uiState.update { it.copy(phase = LoginPhase.FORM, savedChecked = false, copied = false, signedIn = true) }
        _recoveryCodes.value = null
        savedState.remove<Boolean>(KEY_CODES_PENDING)
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

    companion object {
        const val KEY_CODES_PENDING = "codes_pending"
        private const val ZERO = '\u0000'
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
