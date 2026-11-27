package com.simposio.taskeer.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.SyncRepository
import com.simposio.taskeer.domain.sync.SyncNotice
import com.simposio.taskeer.domain.sync.SyncNoticeRules
import com.simposio.taskeer.domain.sync.SyncStatus
import com.simposio.taskeer.domain.sync.SyncStatusRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SyncUiState(
    val status: SyncStatus,
    val visible: Boolean,
    val accountDialogOpen: Boolean,
)

class SyncViewModel(private val repository: SyncRepository) : ViewModel() {

    val state: StateFlow<SyncUiState> = combine(repository.status, repository.accountSwitchPrompt) { snapshot, prompt ->
        val status = SyncStatusRules.resolve(snapshot)
        SyncUiState(status, SyncStatusRules.isVisible(status), accountDialogOpen = prompt)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        SyncUiState(SyncStatus.Idle(null), visible = false, accountDialogOpen = false),
    )

    val noticeMessages: Flow<String> = repository.notices.map { notice: SyncNotice -> SyncNoticeRules.message(notice) }

    fun sync() = repository.requestSync()

    fun onForeground() = repository.notifyForeground()

    fun confirmAccountWipe() {
        viewModelScope.launch { repository.confirmAccountWipe() }
    }

    fun cancelAccountSwitch() = repository.cancelAccountSwitch()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
