package com.simposio.taskeer.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

class AppViewModel(private val preferences: PreferencesRepository) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            ThemeMode.AUTO,
        )

    val dynamicColor: StateFlow<Boolean> = preferences.dynamicColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), true)

    private val _openViewerRequested = MutableStateFlow(false)

    val openViewerRequested: StateFlow<Boolean> = _openViewerRequested.asStateFlow()

    fun requestOpenViewer() {
        _openViewerRequested.value = true
    }

    fun consumeOpenViewer() {
        _openViewerRequested.value = false
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
