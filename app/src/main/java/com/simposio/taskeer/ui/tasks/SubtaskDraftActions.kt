package com.simposio.taskeer.ui.tasks

import java.time.LocalDateTime

data class SubtaskDraftActions(
    val onNewTitleChange: (String) -> Unit,
    val onAdd: () -> Unit,
    val onStartEdit: (String) -> Unit,
    val onEditTitleChange: (String) -> Unit,
    val onEditDueAtChange: (LocalDateTime?) -> Unit,
    val onEditAssigneeChange: (String?) -> Unit,
    val onSaveEdit: () -> Unit,
    val onCancelEdit: () -> Unit,
    val onDelete: (String) -> Unit,
) {
    companion object {
        val None = SubtaskDraftActions({}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}
