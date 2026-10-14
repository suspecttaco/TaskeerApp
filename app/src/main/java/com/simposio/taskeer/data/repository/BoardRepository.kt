package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.DomainResult
import kotlinx.coroutines.flow.Flow

interface BoardRepository {
    fun observeBoards(): Flow<List<Board>>

    suspend fun createBoard(name: String): DomainResult<Board>

    suspend fun renameBoard(id: String, name: String): DomainResult<Board>

    suspend fun deleteBoard(id: String): DomainResult<Unit>
}
