package com.simposio.taskeer.domain.board

import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.afterLocalChange
import com.simposio.taskeer.domain.model.afterLocalDelete
import com.simposio.taskeer.domain.model.newClientId
import com.simposio.taskeer.domain.model.newSyncMetadata
import java.time.Instant

const val BOARD_NAME_REQUIRED = "BOARD_NAME_REQUIRED"
const val BOARD_NAME_TOO_SHORT = "BOARD_NAME_TOO_SHORT"
const val BOARD_NAME_TOO_LONG = "BOARD_NAME_TOO_LONG"
const val BOARD_NAME_MIN_LENGTH = 2
const val BOARD_NAME_MAX_LENGTH = 150

fun newBoard(name: String, now: Instant, clientId: String = newClientId()): DomainResult<Board> {
    val trimmed = name.trim()
    nameFailure(trimmed)?.let { return it }
    return DomainResult.Success(
        Board(
            name = trimmed,
            createdAt = now,
            sync = newSyncMetadata(now, clientId),
        ),
    )
}

fun Board.renamed(name: String, now: Instant): DomainResult<Board> {
    val trimmed = name.trim()
    nameFailure(trimmed)?.let { return it }
    return DomainResult.Success(copy(name = trimmed, sync = sync.afterLocalChange(now)))
}

fun Board.softDeleted(now: Instant): Board = copy(sync = sync.afterLocalDelete(now))

private fun nameFailure(trimmed: String): DomainResult.Failure? = when {
    trimmed.isEmpty() -> validation(BOARD_NAME_REQUIRED, "El nombre del tablero es obligatorio")
    trimmed.length < BOARD_NAME_MIN_LENGTH -> validation(
        BOARD_NAME_TOO_SHORT,
        "El nombre del tablero debe tener al menos $BOARD_NAME_MIN_LENGTH caracteres",
    )
    trimmed.length > BOARD_NAME_MAX_LENGTH -> validation(
        BOARD_NAME_TOO_LONG,
        "El nombre del tablero no puede superar los $BOARD_NAME_MAX_LENGTH caracteres",
    )
    else -> null
}

private fun validation(code: String, message: String): DomainResult.Failure =
    DomainResult.Failure(DomainError.Validation(code, message))
