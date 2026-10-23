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
const val TASK_NOT_FOUND = "TASK_NOT_FOUND"

fun newTask(
    columnId: String,
    draft: TaskDraft,
    position: Int,
    now: Instant,
    clientId: String = newClientId(),
): DomainResult<Task> {
    val title = draft.title.trim()
    if (title.isEmpty()) {
        return titleRequired()
    }
    return DomainResult.Success(
        Task(
            columnId = columnId,
            title = title,
            description = draft.description.normalized(),
            priority = draft.priority,
            dueAt = draft.dueAt,
            assigneeId = draft.assigneeId.normalized(),
            position = position,
            sync = newSyncMetadata(now, clientId),
        ),
    )
}

fun Task.updated(draft: TaskDraft, now: Instant): DomainResult<Task> {
    val title = draft.title.trim()
    if (title.isEmpty()) {
        return titleRequired()
    }
    return DomainResult.Success(
        copy(
            title = title,
            description = draft.description.normalized(),
            priority = draft.priority,
            dueAt = draft.dueAt,
            assigneeId = draft.assigneeId.normalized(),
            sync = sync.afterLocalChange(now),
        ),
    )
}

fun Task.softDeleted(now: Instant): Task = copy(sync = sync.afterLocalDelete(now))

private fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun titleRequired(): DomainResult.Failure =
    DomainResult.Failure(
        DomainError.Validation(TASK_TITLE_REQUIRED, "El título de la tarea es obligatorio"),
    )
