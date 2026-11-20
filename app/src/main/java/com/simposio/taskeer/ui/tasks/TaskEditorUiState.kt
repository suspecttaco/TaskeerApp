package com.simposio.taskeer.ui.tasks

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.model.TaskListSummary
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.model.TaskRepeat
import com.simposio.taskeer.domain.task.RepeatPreset
import com.simposio.taskeer.domain.task.presetOf
import java.time.Instant
import java.time.LocalDateTime

data class TaskEditorUiState(
    val isOpen: Boolean = false,
    val taskId: String? = null,
    val columnId: String? = null,
    val title: String = "",
    val description: String = "",
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueAtLocal: LocalDateTime? = null,
    val assigneeId: String? = LocalUser.ID,
    val error: DomainError? = null,
    val isSaving: Boolean = false,
    val subtaskDrafts: List<SubtaskDraftItem> = emptyList(),
    val newSubtaskTitle: String = "",
    val editingDraftId: String? = null,
    val editingDraftTitle: String = "",
    val editingDraftDueAtLocal: LocalDateTime? = null,
    val editingDraftAssigneeId: String? = null,
    val reminderDraft: Instant? = null,
    val reminderDraftLocal: LocalDateTime? = null,
    val reminderError: String? = null,
    val repeat: TaskRepeat? = null,
    val repeatCustom: Boolean = false,
    val repeatIntervalText: String = "1",
    // Fecha y regla con las que se abrió la tarea: mientras la fecha no cambie se conserva su ancla.
    val initialDueAtLocal: LocalDateTime? = null,
    val initialRepeat: TaskRepeat? = null,
    val completed: Boolean = false,
    val inMyDay: Boolean = false,
    val listId: String? = null,
    val lists: List<TaskListSummary> = emptyList(),
    val boardName: String? = null,
    val columnName: String? = null,
    val panel: EditorPanel = EditorPanel.None,
    val isClosing: Boolean = false,
    val initialFields: EditorFields = EditorFields(),
) {
    val repeatPreset: RepeatPreset get() = if (repeatCustom) RepeatPreset.CUSTOM else presetOf(repeat)
    val repeatEnabled: Boolean get() = dueAtLocal != null
    val isBoardTask: Boolean get() = columnId != null
    val isDirty: Boolean get() = fields() != initialFields

    // Mi día y la lista solo son borrador al crear: en una tarea guardada se escriben al instante.
    fun fields(): EditorFields = EditorFields(
        title = title.trim(),
        description = description.trim(),
        priority = priority,
        dueAtLocal = dueAtLocal,
        assigneeId = assigneeId,
        repeat = repeat,
        subtasks = subtaskDrafts.map { it.id to it.title },
        reminderDraft = reminderDraft,
        inMyDay = inMyDay.takeIf { taskId == null } ?: false,
        listId = listId.takeIf { taskId == null },
    )
}

data class EditorFields(
    val title: String = "",
    val description: String = "",
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueAtLocal: LocalDateTime? = null,
    val assigneeId: String? = LocalUser.ID,
    val repeat: TaskRepeat? = null,
    val subtasks: List<Pair<String, String>> = emptyList(),
    val reminderDraft: Instant? = null,
    val inMyDay: Boolean = false,
    val listId: String? = null,
)

sealed interface EditorPanel {
    data object None : EditorPanel
    data class DateTime(val target: PickerTarget, val pickedEpochDay: Long? = null) : EditorPanel
    data object ListPicker : EditorPanel
    data object DiscardConfirm : EditorPanel
}

enum class PickerTarget { TASK_DUE, REMINDER, SUBTASK_DUE }

fun EditorPanel.parent(): EditorPanel = when (this) {
    is EditorPanel.DateTime -> if (pickedEpochDay != null) EditorPanel.DateTime(target) else EditorPanel.None
    EditorPanel.DiscardConfirm, EditorPanel.ListPicker, EditorPanel.None -> EditorPanel.None
}
