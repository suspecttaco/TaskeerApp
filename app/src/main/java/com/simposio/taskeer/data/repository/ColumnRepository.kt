package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.DomainResult
import kotlinx.coroutines.flow.Flow

interface ColumnRepository {
    fun observeColumns(boardId: String): Flow<List<Column>>

    suspend fun createColumn(boardId: String, name: String): DomainResult<Column>

    suspend fun renameColumn(id: String, name: String): DomainResult<Column>

    suspend fun setWipLimit(columnId: String, limit: Int?): DomainResult<Column>

    suspend fun moveColumn(id: String, toIndex: Int): DomainResult<Column>

    suspend fun deleteColumn(id: String): DomainResult<Unit>
}
