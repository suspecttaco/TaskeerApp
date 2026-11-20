package com.simposio.taskeer.ui.tasks

import com.simposio.taskeer.domain.model.TaskPriority
import java.time.LocalTime

data class TaskEditorActions(
    val onTitleChange: (String) -> Unit,
    val onDescriptionChange: (String) -> Unit,
    val onPriorityChange: (TaskPriority) -> Unit,
    val onAssigneeChange: (String?) -> Unit,
    val onToggleCompleted: () -> Unit,
    val onToggleMyDay: () -> Unit,
    val onOpenPanel: (EditorPanel) -> Unit,
    val onListChosen: (String) -> Unit,
    val onPickerDate: (Long) -> Unit,
    val onPickerTime: (LocalTime) -> Unit,
    val onClearDue: () -> Unit,
    val onBack: () -> Unit,
    val onRequestClose: () -> Unit,
    val onConfirmDiscard: () -> Unit,
    val onKeepEditing: () -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
    val onClosed: () -> Unit,
) {
    companion object {
        val None = TaskEditorActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}
