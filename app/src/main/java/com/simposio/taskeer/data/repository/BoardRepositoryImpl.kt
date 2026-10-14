package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.BoardDao
import com.simposio.taskeer.data.local.toBoard
import com.simposio.taskeer.data.local.toEntity
import com.simposio.taskeer.domain.board.newBoard
import com.simposio.taskeer.domain.board.renamed
import com.simposio.taskeer.domain.board.softDeleted
import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BoardRepositoryImpl(
    private val dao: BoardDao,
    private val now: () -> Instant = Instant::now,
) : BoardRepository {

    override fun observeBoards(): Flow<List<Board>> =
        dao.observeActive().map { entities -> entities.map { it.toBoard() } }

    override suspend fun createBoard(name: String): DomainResult<Board> =
        when (val result = newBoard(name, now())) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> {
                dao.upsert(result.value.toEntity())
                DomainResult.Success(result.value)
            }
        }

    override suspend fun renameBoard(id: String, name: String): DomainResult<Board> {
        val current = activeBoard(id) ?: return notFound()
        return when (val result = current.renamed(name, now())) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> {
                dao.upsert(result.value.toEntity())
                DomainResult.Success(result.value)
            }
        }
    }

    override suspend fun deleteBoard(id: String): DomainResult<Unit> {
        val current = activeBoard(id) ?: return notFound()
        dao.upsert(current.softDeleted(now()).toEntity())
        return DomainResult.Success(Unit)
    }

    private suspend fun activeBoard(id: String): Board? =
        dao.findById(id)?.toBoard()?.takeUnless { it.isDeleted }

    private fun notFound(): DomainResult.Failure =
        DomainResult.Failure(DomainError.NotFound(BOARD_NOT_FOUND, "El tablero no existe"))

    private companion object {
        const val BOARD_NOT_FOUND = "BOARD_NOT_FOUND"
    }
}
