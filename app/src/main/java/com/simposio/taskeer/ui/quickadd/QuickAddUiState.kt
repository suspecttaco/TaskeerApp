package com.simposio.taskeer.ui.quickadd

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.planned.DayLabel
import com.simposio.taskeer.domain.task.QuickDateChoice
import java.time.LocalDate

sealed interface QuickAddContext {
    data object Default : QuickAddContext

    data object MyDay : QuickAddContext

    data class InList(val listId: String) : QuickAddContext
}

data class QuickAddListOption(val id: String, val name: String, val taskCount: Int, val selected: Boolean)

data class QuickAddUiState(
    val isOpen: Boolean = false,
    val title: String = "",
    val dateChoice: QuickDateChoice = QuickDateChoice.None,
    val dueDate: LocalDate? = null,
    val dueLabel: DayLabel? = null,
    val listOptions: List<QuickAddListOption> = emptyList(),
    val selectedListName: String? = null,
    val isListPanelOpen: Boolean = false,
    val isDatePickerOpen: Boolean = false,
    val isSaving: Boolean = false,
    val error: DomainError? = null,
) {
    val canSubmit: Boolean get() = title.isNotBlank() && !isSaving
}
