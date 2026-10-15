package com.simposio.taskeer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ColumnDao {
    @Query("SELECT * FROM columns WHERE boardId = :boardId AND deletedAt IS NULL ORDER BY position ASC, id ASC")
    abstract fun observeByBoard(boardId: String): Flow<List<ColumnEntity>>

    @Query("SELECT * FROM columns WHERE boardId = :boardId AND deletedAt IS NULL ORDER BY position ASC, id ASC")
    abstract suspend fun findActiveByBoard(boardId: String): List<ColumnEntity>

    @Query("SELECT * FROM columns WHERE id = :id")
    abstract suspend fun findById(id: String): ColumnEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(column: ColumnEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(columns: List<ColumnEntity>)

    // La siembra atómica tablero+columnas vive en este DAO porque Room solo envuelve
    // en una transacción sentencias del mismo DAO; el repositorio no recibe la base.
    // insertBoard duplica la firma de BoardDao.upsert a propósito: es el precio de la
    // atomicidad entre dos tablas sin exponer el almacén fuera de data/local/.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertBoard(board: BoardEntity)

    @Transaction
    open suspend fun insertBoardWithSeed(board: BoardEntity, columns: List<ColumnEntity>) {
        insertBoard(board)
        upsertAll(columns)
    }
}
