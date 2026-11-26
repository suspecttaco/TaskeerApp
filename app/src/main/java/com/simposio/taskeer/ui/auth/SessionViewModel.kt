package com.simposio.taskeer.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.AuthRepository
import com.simposio.taskeer.domain.auth.SessionState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

enum class SessionMessage { LOGGED_OUT, LOGGED_OUT_SERVER_UNREACHABLE }

class SessionViewModel(private val auth: AuthRepository) : ViewModel() {

    val state: StateFlow<SessionState> = auth.sessionState

    private val _messages = MutableSharedFlow<SessionMessage>(extraBufferCapacity = 1)
    val messages: SharedFlow<SessionMessage> = _messages.asSharedFlow()

    private var loggingOut = false

    fun logout() {
        if (loggingOut) return
        loggingOut = true
        viewModelScope.launch {
            try {
                val outcome = auth.logout()
                _messages.emit(
                    if (outcome.serverRevoked) SessionMessage.LOGGED_OUT else SessionMessage.LOGGED_OUT_SERVER_UNREACHABLE,
                )
            } finally {
                loggingOut = false
            }
        }
    }

    fun retryResume() {
        viewModelScope.launch { auth.resume() }
    }

    fun acknowledgeExpiration() {
        auth.acknowledgeExpiration()
    }
}
