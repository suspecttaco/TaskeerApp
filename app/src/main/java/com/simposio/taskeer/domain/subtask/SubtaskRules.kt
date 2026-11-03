package com.simposio.taskeer.domain.subtask

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.Subtask
import com.simposio.taskeer.domain.model.SubtaskDraft
import com.simposio.taskeer.domain.model.afterLocalChange
import com.simposio.taskeer.domain.model.afterLocalDelete
import com.simposio.taskeer.domain.model.newClientId
import com.simposio.taskeer.domain.model.newSyncMetadata
import java.time.Instant

const val SUBTASK_TITLE_REQUIRED = "SUBTASK_TITLE_REQUIRED"
const val SUBTASK_TITLE_TOO_LONG = "SUBTASK_TITLE_TOO_LONG"
const val SUBTASK_TITLE_MAX_LENGTH = 200
const val SUBTASK_NOT_FOUND = "SUBTASK_NOT_FOUND"

fun newSubtask(
    taskId: String,
    draft: SubtaskDraft,
    position: Int,
    now: Instant,
    clientId: String = newClientId(),
): DomainResult<Subtask> {
    val title = draft.title.trim()
    titleFailure(title)?.let { return it }
    return DomainResult.Success(
        Subtask(
            taskId = taskId,
            title = title,
            completed = false,
            position = position,
            assigneeId = draft.assigneeId.normalized(),
            dueAt = draft.dueAt,
            createdAt = now,
            sync = newSyncMetadata(now, clientId),
        ),
    )
}

fun Subtask.updated(draft: SubtaskDraft, now: Instant): DomainResult<Subtask> {
    val title = draft.title.trim()
    titleFailure(title)?.let { return it }
    return DomainResult.Success(
        copy(
            title = title,
            assigneeId = draft.assigneeId.normalized(),
            dueAt = draft.dueAt,
            sync = sync.afterLocalChange(now),
        ),
    )
}

fun Subtask.withCompleted(completed: Boolean, now: Instant): Subtask =
    copy(completed = completed, sync = sync.afterLocalChange(now))

fun Subtask.softDeleted(now: Instant): Subtask = copy(sync = sync.afterLocalDelete(now))

private fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun titleFailure(title: String): DomainResult.Failure? = when {
    title.isEmpty() -> DomainResult.Failure(
        DomainError.Validation(SUBTASK_TITLE_REQUIRED, "El título de la subtarea es obligatorio"),
    )
    title.length > SUBTASK_TITLE_MAX_LENGTH -> DomainResult.Failure(
        DomainError.Validation(
            SUBTASK_TITLE_TOO_LONG,
            "El título de la subtarea no puede superar los $SUBTASK_TITLE_MAX_LENGTH caracteres",
        ),
    )
    else -> null
}
