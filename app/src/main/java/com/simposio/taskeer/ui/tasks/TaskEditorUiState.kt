package com.simposio.taskeer.ui.tasks

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.LocalUser
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
) {
    val repeatPreset: RepeatPreset get() = if (repeatCustom) RepeatPreset.CUSTOM else presetOf(repeat)
    val repeatEnabled: Boolean get() = dueAtLocal != null
}
