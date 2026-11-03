package com.simposio.taskeer.domain.task

import com.simposio.taskeer.domain.model.Column
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
const val TASK_INDEX_OUT_OF_BOUNDS = "TASK_INDEX_OUT_OF_BOUNDS"

// Código local: la API solo conoce CROSS_WORKSPACE_MOVE, concepto que no existe sin workspaces.
const val COLUMN_BOARD_MISMATCH = "COLUMN_BOARD_MISMATCH"

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

fun checkSameBoard(sourceBoardId: String, destination: Column): DomainError? =
    if (destination.boardId == sourceBoardId) {
        null
    } else {
        DomainError.Validation(COLUMN_BOARD_MISMATCH, "La columna destino pertenece a otro tablero")
    }

fun movedTask(
    task: Task,
    source: List<Task>,
    destination: List<Task>,
    toColumnId: String,
    toIndex: Int,
    now: Instant,
): DomainResult<List<Task>> {
    val ordered = compareBy<Task>({ it.position }, { it.id })
    val sameColumn = task.columnId == toColumnId
    val sourceRest = source.filter { it.id != task.id }.sortedWith(ordered)
    val destinationRest = if (sameColumn) sourceRest else destination.filter { it.id != task.id }.sortedWith(ordered)
    val maxIndex = if (sameColumn) sourceRest.size else destinationRest.size
    if (toIndex !in 0..maxIndex) {
        return validation(TASK_INDEX_OUT_OF_BOUNDS, "El índice de destino está fuera de rango")
    }
    val originals = (source + destination + task).associateBy { it.id }
    val newDestination = destinationRest.toMutableList().apply { add(toIndex, task) }
    val result = buildList {
        if (!sameColumn) addAll(sourceRest.renumbered(task.columnId, now))
        addAll(newDestination.renumbered(toColumnId, now))
    }
    return DomainResult.Success(result.filter { it != originals[it.id] })
}

private fun List<Task>.renumbered(columnId: String, now: Instant): List<Task> = mapIndexed { index, task ->
    if (task.position == index && task.columnId == columnId) {
        task
    } else {
        task.copy(position = index, columnId = columnId, sync = task.sync.afterLocalChange(now))
    }
}

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
