package com.simposio.taskeer.domain.task

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.model.afterLocalChange
import com.simposio.taskeer.domain.model.afterLocalDelete
import com.simposio.taskeer.domain.model.newClientId
import com.simposio.taskeer.domain.model.newSyncMetadata
import java.time.Instant

const val TASK_TITLE_REQUIRED = "TASK_TITLE_REQUIRED"
const val TASK_TITLE_TOO_LONG = "TASK_TITLE_TOO_LONG"
const val TASK_TITLE_MAX_LENGTH = 200
const val TASK_DESCRIPTION_TOO_LONG = "TASK_DESCRIPTION_TOO_LONG"
const val TASK_DESCRIPTION_MAX_LENGTH = 5000
const val TASK_NOT_FOUND = "TASK_NOT_FOUND"

fun newTask(
    columnId: String,
    draft: TaskDraft,
    position: Int,
    now: Instant,
    clientId: String = newClientId(),
): DomainResult<Task> {
    val title = draft.title.trim()
    val description = draft.description.normalized()
    textFailure(title, description)?.let { return it }
    return DomainResult.Success(
        Task(
            columnId = columnId,
            title = title,
            description = description,
            priority = draft.priority,
            dueAt = draft.dueAt,
            assigneeId = draft.assigneeId.normalized(),
            position = position,
            createdAt = now,
            sync = newSyncMetadata(now, clientId),
        ),
    )
}

fun Task.updated(draft: TaskDraft, now: Instant): DomainResult<Task> {
    val title = draft.title.trim()
    val description = draft.description.normalized()
    textFailure(title, description)?.let { return it }
    return DomainResult.Success(
        copy(
            title = title,
            description = description,
            priority = draft.priority,
            dueAt = draft.dueAt,
            assigneeId = draft.assigneeId.normalized(),
            sync = sync.afterLocalChange(now),
        ),
    )
}

fun Task.softDeleted(now: Instant): Task = copy(sync = sync.afterLocalDelete(now))

private fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun textFailure(title: String, description: String?): DomainResult.Failure? = when {
    title.isEmpty() -> validation(TASK_TITLE_REQUIRED, "El título de la tarea es obligatorio")
    title.length > TASK_TITLE_MAX_LENGTH -> validation(
        TASK_TITLE_TOO_LONG,
        "El título de la tarea no puede superar los $TASK_TITLE_MAX_LENGTH caracteres",
    )
    description != null && description.length > TASK_DESCRIPTION_MAX_LENGTH -> validation(
        TASK_DESCRIPTION_TOO_LONG,
        "La descripción de la tarea no puede superar los $TASK_DESCRIPTION_MAX_LENGTH caracteres",
    )
    else -> null
}

private fun validation(code: String, message: String): DomainResult.Failure =
    DomainResult.Failure(DomainError.Validation(code, message))
