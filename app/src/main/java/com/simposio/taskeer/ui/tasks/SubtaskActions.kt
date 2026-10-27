package com.simposio.taskeer.ui.tasks

data class SubtaskActions(
    val onNewTitleChange: (String) -> Unit,
    val onAdd: () -> Unit,
    val onToggle: (String, Boolean) -> Unit,
    val onStartEdit: (String) -> Unit,
    val onEditTitleChange: (String) -> Unit,
    val onSaveEdit: () -> Unit,
    val onCancelEdit: () -> Unit,
    val onDelete: (String) -> Unit,
) {
    companion object {
        val None = SubtaskActions({}, {}, { _, _ -> }, {}, {}, {}, {}, {})
    }
}
