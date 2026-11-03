package com.simposio.taskeer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TaskDao {
    @Query(
        "SELECT tasks.* FROM tasks INNER JOIN columns ON tasks.columnId = columns.id " +
            "WHERE columns.boardId = :boardId AND columns.deletedAt IS NULL " +
            "AND tasks.deletedAt IS NULL ORDER BY tasks.position ASC, tasks.id ASC",
    )
    abstract fun observeByBoard(boardId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE columnId = :columnId AND deletedAt IS NULL ORDER BY position ASC, id ASC")
    abstract suspend fun findActiveByColumn(columnId: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    abstract suspend fun findById(id: String): TaskEntity?

    // Sin filtro de deletedAt: las tareas borradas lógicamente también cuentan, como el
    // 409 COLUMN_NOT_EMPTY de la API.
    @Query("SELECT COUNT(*) FROM tasks WHERE columnId = :columnId")
    abstract suspend fun countByColumn(columnId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(task: TaskEntity)

    // Una sola sentencia: Room la ejecuta de forma atómica sin @Transaction.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(tasks: List<TaskEntity>)

    @Query(
        "UPDATE subtasks SET deletedAt = :deletedAt, updatedAt = :deletedAt, pending = 1 " +
            "WHERE taskId = :taskId AND deletedAt IS NULL",
    )
    abstract suspend fun softDeleteSubtasksOfTask(taskId: String, deletedAt: Long)

    // Room solo envuelve en una transacción sentencias del mismo DAO: por eso la cascada vive aquí.
    @Transaction
    open suspend fun upsertDeletedWithSubtasks(task: TaskEntity, deletedAt: Long) {
        upsert(task)
        softDeleteSubtasksOfTask(task.id, deletedAt)
    }
}
