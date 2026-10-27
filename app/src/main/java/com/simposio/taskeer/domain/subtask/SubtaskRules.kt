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
const val SUBTASK_NOT_FOUND = "SUBTASK_NOT_FOUND"

fun newSubtask(
    taskId: String,
    draft: SubtaskDraft,
    position: Int,
    now: Instant,
    clientId: String = newClientId(),
): DomainResult<Subtask> {
    val title = draft.title.trim()
    if (title.isEmpty()) {
        return titleRequired()
    }
    return DomainResult.Success(
        Subtask(
            taskId = taskId,
            title = title,
            completed = false,
            position = position,
            sync = newSyncMetadata(now, clientId),
        ),
    )
}

fun Subtask.retitled(draft: SubtaskDraft, now: Instant): DomainResult<Subtask> {
    val title = draft.title.trim()
    if (title.isEmpty()) {
        return titleRequired()
    }
    return DomainResult.Success(copy(title = title, sync = sync.afterLocalChange(now)))
}

fun Subtask.withCompleted(completed: Boolean, now: Instant): Subtask =
    copy(completed = completed, sync = sync.afterLocalChange(now))

fun Subtask.softDeleted(now: Instant): Subtask = copy(sync = sync.afterLocalDelete(now))

private fun titleRequired(): DomainResult.Failure =
    DomainResult.Failure(
        DomainError.Validation(SUBTASK_TITLE_REQUIRED, "El título de la subtarea es obligatorio"),
    )
