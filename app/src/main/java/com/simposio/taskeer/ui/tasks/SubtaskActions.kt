package com.simposio.taskeer.ui.tasks

import java.time.LocalDateTime

data class SubtaskActions(
    val onNewTitleChange: (String) -> Unit,
    val onAdd: () -> Unit,
    val onToggle: (String, Boolean) -> Unit,
    val onStartEdit: (String) -> Unit,
    val onEditTitleChange: (String) -> Unit,
    val onEditDueAtChange: (LocalDateTime?) -> Unit,
    val onEditAssigneeChange: (String?) -> Unit,
    val onSaveEdit: () -> Unit,
    val onCancelEdit: () -> Unit,
    val onDelete: (String) -> Unit,
) {
    companion object {
        val None = SubtaskActions({}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {})
    }
}
