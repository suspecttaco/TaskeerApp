package com.simposio.taskeer.ui.quickadd

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.planned.DayLabel
import java.time.LocalDate

sealed interface QuickAddContext {
    data object Default : QuickAddContext

    data object MyDay : QuickAddContext

    data class InList(val listId: String) : QuickAddContext
}

data class QuickAddUiState(
    val isOpen: Boolean = false,
    val title: String = "",
    val dueDate: LocalDate? = null,
    val dueLabel: DayLabel? = null,
    val isSaving: Boolean = false,
    val error: DomainError? = null,
) {
    val canSubmit: Boolean get() = title.isNotBlank() && !isSaving
}
