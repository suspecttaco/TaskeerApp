package com.simposio.taskeer.ui.myday

import com.simposio.taskeer.domain.model.BoardDestination
import com.simposio.taskeer.domain.model.DomainError

sealed interface QuickAddDestination {
    data object Inbox : QuickAddDestination

    data class InColumn(val columnId: String) : QuickAddDestination
}

data class QuickAddUiState(
    val title: String = "",
    val destination: QuickAddDestination = QuickAddDestination.Inbox,
    val destinationLabel: String? = null,
    val destinations: List<BoardDestination> = emptyList(),
    val isPickerOpen: Boolean = false,
    val isSaving: Boolean = false,
    val error: DomainError? = null,
)
