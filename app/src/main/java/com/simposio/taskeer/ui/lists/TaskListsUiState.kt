package com.simposio.taskeer.ui.lists

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.TaskListSummary

sealed interface ListDialog {
    data object None : ListDialog

    data object Create : ListDialog

    data class Rename(val listId: String, val currentName: String) : ListDialog

    data class ConfirmDelete(val listId: String, val name: String, val taskCount: Int, val targetName: String) : ListDialog
}

data class TaskListsUiState(
    val lists: List<TaskListSummary> = emptyList(),
    val selectedListId: String? = null,
    val dialog: ListDialog = ListDialog.None,
    val error: DomainError? = null,
) {
    val selected: TaskListSummary? get() = lists.firstOrNull { it.list.id == selectedListId }

    val canRenameSelected: Boolean get() = selected != null

    // La última lista activa no se puede eliminar.
    val canDeleteSelected: Boolean get() = selected != null && lists.size > 1
}
