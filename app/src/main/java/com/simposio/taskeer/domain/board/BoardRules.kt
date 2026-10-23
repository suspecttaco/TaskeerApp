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

fun newBoard(name: String, now: Instant, clientId: String = newClientId()): DomainResult<Board> {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) {
        return nameRequired()
    }
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
    if (trimmed.isEmpty()) {
        return nameRequired()
    }
    return DomainResult.Success(copy(name = trimmed, sync = sync.afterLocalChange(now)))
}

fun Board.softDeleted(now: Instant): Board = copy(sync = sync.afterLocalDelete(now))

private fun nameRequired(): DomainResult.Failure =
    DomainResult.Failure(
        DomainError.Validation(BOARD_NAME_REQUIRED, "El nombre del tablero es obligatorio"),
    )
