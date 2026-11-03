package com.simposio.taskeer.domain.column

import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.afterLocalChange
import com.simposio.taskeer.domain.model.afterLocalDelete
import com.simposio.taskeer.domain.model.newClientId
import com.simposio.taskeer.domain.model.newSyncMetadata
import java.time.Instant

const val COLUMN_NAME_REQUIRED = "COLUMN_NAME_REQUIRED"
const val COLUMN_NAME_TOO_LONG = "COLUMN_NAME_TOO_LONG"
const val COLUMN_NAME_MAX_LENGTH = 100
const val COLUMN_WIP_LIMIT_INVALID = "COLUMN_WIP_LIMIT_INVALID"
const val COLUMN_WIP_LIMIT_MIN = 1
const val COLUMN_NOT_EMPTY = "COLUMN_NOT_EMPTY"
const val COLUMN_INDEX_OUT_OF_BOUNDS = "COLUMN_INDEX_OUT_OF_BOUNDS"
const val COLUMN_NOT_FOUND = "COLUMN_NOT_FOUND"

fun defaultBoardColumns(
    boardId: String,
    now: Instant,
    newId: () -> String = ::newClientId,
): List<Column> = listOf(
    defaultColumn(boardId, "Por hacer", 0, isDoneColumn = false, now = now, clientId = newId()),
    defaultColumn(boardId, "En progreso", 1, isDoneColumn = false, now = now, clientId = newId()),
    defaultColumn(boardId, "Hecho", 2, isDoneColumn = true, now = now, clientId = newId()),
)

fun newColumn(
    boardId: String,
    name: String,
    position: Int,
    now: Instant,
    clientId: String = newClientId(),
): DomainResult<Column> {
    val trimmed = name.trim()
    nameFailure(trimmed)?.let { return it }
    return DomainResult.Success(
        Column(
            boardId = boardId,
            name = trimmed,
            position = position,
            wipLimit = null,
            isDoneColumn = false,
            createdAt = now,
            sync = newSyncMetadata(now, clientId),
        ),
    )
}

fun Column.renamed(name: String, now: Instant): DomainResult<Column> {
    val trimmed = name.trim()
    nameFailure(trimmed)?.let { return it }
    return DomainResult.Success(copy(name = trimmed, sync = sync.afterLocalChange(now)))
}

fun Column.withWipLimit(wipLimit: Int?, now: Instant): DomainResult<Column> {
    if (wipLimit != null && wipLimit < COLUMN_WIP_LIMIT_MIN) {
        return DomainResult.Failure(
            DomainError.Validation(
                COLUMN_WIP_LIMIT_INVALID,
                "El valor mínimo del límite WIP es $COLUMN_WIP_LIMIT_MIN",
            ),
        )
    }
    return DomainResult.Success(copy(wipLimit = wipLimit, sync = sync.afterLocalChange(now)))
}

fun Column.softDeleted(now: Instant): Column = copy(sync = sync.afterLocalDelete(now))

fun Column.deletedIfEmpty(taskCount: Int, now: Instant): DomainResult<Column> =
    if (taskCount > 0) {
        DomainResult.Failure(
            DomainError.Conflict(COLUMN_NOT_EMPTY, "La columna tiene tareas; no se puede borrar"),
        )
    } else {
        DomainResult.Success(softDeleted(now))
    }

fun List<Column>.renumbered(now: Instant): List<Column> = mapIndexed { index, column ->
    if (column.position == index) {
        column
    } else {
        column.copy(position = index, sync = column.sync.afterLocalChange(now))
    }
}

fun List<Column>.movedTo(columnId: String, toIndex: Int, now: Instant): DomainResult<List<Column>> {
    val fromIndex = indexOfFirst { it.id == columnId }
    if (fromIndex < 0) {
        return DomainResult.Failure(DomainError.NotFound(COLUMN_NOT_FOUND, "La columna no existe"))
    }
    if (toIndex !in indices) {
        return DomainResult.Failure(
            DomainError.Validation(
                COLUMN_INDEX_OUT_OF_BOUNDS,
                "El índice de destino está fuera de rango",
            ),
        )
    }
    if (fromIndex == toIndex) {
        return DomainResult.Success(this)
    }
    val reordered = toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    return DomainResult.Success(reordered.renumbered(now))
}

private fun defaultColumn(
    boardId: String,
    name: String,
    position: Int,
    isDoneColumn: Boolean,
    now: Instant,
    clientId: String,
): Column = Column(
    boardId = boardId,
    name = name,
    position = position,
    wipLimit = null,
    isDoneColumn = isDoneColumn,
    createdAt = now,
    sync = newSyncMetadata(now, clientId),
)

private fun nameFailure(trimmed: String): DomainResult.Failure? = when {
    trimmed.isEmpty() -> DomainResult.Failure(
        DomainError.Validation(COLUMN_NAME_REQUIRED, "El nombre de la columna es obligatorio"),
    )
    trimmed.length > COLUMN_NAME_MAX_LENGTH -> DomainResult.Failure(
        DomainError.Validation(
            COLUMN_NAME_TOO_LONG,
            "El nombre de la columna no puede superar los $COLUMN_NAME_MAX_LENGTH caracteres",
        ),
    )
    else -> null
}
