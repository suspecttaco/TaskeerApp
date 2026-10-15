package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.BoardDao
import com.simposio.taskeer.data.local.ColumnDao
import com.simposio.taskeer.data.local.toBoard
import com.simposio.taskeer.data.local.toColumn
import com.simposio.taskeer.data.local.toEntity
import com.simposio.taskeer.domain.column.COLUMN_NOT_FOUND
import com.simposio.taskeer.domain.column.deletedIfEmpty
import com.simposio.taskeer.domain.column.movedTo
import com.simposio.taskeer.domain.column.newColumn
import com.simposio.taskeer.domain.column.renamed
import com.simposio.taskeer.domain.column.renumbered
import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ColumnRepositoryImpl(
    private val boardDao: BoardDao,
    private val dao: ColumnDao,
    // Costura hasta task-editor: la tabla tasks todavía no existe, así que hoy ninguna
    // columna puede tener tareas. task-editor debe inyectar el conteo real (sin filtrar
    // deletedAt, como el 409 COLUMN_NOT_EMPTY de la API) al crear su TaskDao.
    private val columnTaskCount: suspend (columnId: String) -> Int = { 0 },
    private val now: () -> Instant = Instant::now,
) : ColumnRepository {

    override fun observeColumns(boardId: String): Flow<List<Column>> =
        dao.observeByBoard(boardId).map { entities -> entities.map { it.toColumn() } }

    override suspend fun createColumn(boardId: String, name: String): DomainResult<Column> {
        if (activeBoard(boardId) == null) {
            return boardNotFound()
        }
        val position = dao.findActiveByBoard(boardId).size
        return when (val result = newColumn(boardId, name, position, now())) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> {
                dao.upsert(result.value.toEntity())
                DomainResult.Success(result.value)
            }
        }
    }

    override suspend fun renameColumn(id: String, name: String): DomainResult<Column> {
        val current = activeColumn(id) ?: return columnNotFound()
        return when (val result = current.renamed(name, now())) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> {
                dao.upsert(result.value.toEntity())
                DomainResult.Success(result.value)
            }
        }
    }

    override suspend fun moveColumn(id: String, toIndex: Int): DomainResult<Column> {
        val current = activeColumn(id) ?: return columnNotFound()
        val active = dao.findActiveByBoard(current.boardId).map { it.toColumn() }
        return when (val result = active.movedTo(id, toIndex, now())) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> {
                val reordered = result.value
                val changed = reordered.filter { column ->
                    active.positionOf(column.id) != column.position
                }
                if (changed.isNotEmpty()) {
                    dao.upsertAll(changed.map { it.toEntity() })
                }
                DomainResult.Success(reordered.first { it.id == id })
            }
        }
    }

    override suspend fun deleteColumn(id: String): DomainResult<Unit> {
        val current = activeColumn(id) ?: return columnNotFound()
        return when (val result = current.deletedIfEmpty(columnTaskCount(id), now())) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> {
                val survivors = dao.findActiveByBoard(current.boardId)
                    .map { it.toColumn() }
                    .filterNot { it.id == id }
                val changed = survivors.renumbered(now())
                    .filter { column -> survivors.positionOf(column.id) != column.position }
                dao.upsertAll(listOf(result.value.toEntity()) + changed.map { it.toEntity() })
                DomainResult.Success(Unit)
            }
        }
    }

    private suspend fun activeBoard(boardId: String): Board? =
        boardDao.findById(boardId)?.toBoard()?.takeUnless { it.isDeleted }

    private suspend fun activeColumn(id: String): Column? =
        dao.findById(id)?.toColumn()?.takeUnless { it.isDeleted }

    private fun boardNotFound(): DomainResult.Failure =
        DomainResult.Failure(DomainError.NotFound(BOARD_NOT_FOUND, "El tablero no existe"))

    private fun columnNotFound(): DomainResult.Failure =
        DomainResult.Failure(DomainError.NotFound(COLUMN_NOT_FOUND, "La columna no existe"))

    private companion object {
        const val BOARD_NOT_FOUND = "BOARD_NOT_FOUND"
    }
}

private fun List<Column>.positionOf(columnId: String): Int? =
    firstOrNull { it.id == columnId }?.position
