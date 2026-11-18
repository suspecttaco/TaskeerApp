package com.simposio.taskeer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TaskListDao {
    @Query("SELECT * FROM task_lists WHERE deletedAt IS NULL ORDER BY position ASC, createdAt ASC, id ASC")
    abstract fun observeActive(): Flow<List<TaskListEntity>>

    @Query("SELECT * FROM task_lists WHERE deletedAt IS NULL")
    abstract suspend fun findActive(): List<TaskListEntity>

    @Query("SELECT * FROM task_lists WHERE id = :id")
    abstract suspend fun findById(id: String): TaskListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(list: TaskListEntity)

    @Query("SELECT * FROM tasks WHERE listId = :listId AND deletedAt IS NULL ORDER BY position ASC, createdAt ASC, id ASC")
    abstract suspend fun findActiveTasks(listId: String): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE listId = :listId AND deletedAt IS NULL")
    abstract suspend fun countTasks(listId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE, entity = TaskEntity::class)
    abstract suspend fun upsertTasks(tasks: List<TaskEntity>)

    // Room solo envuelve en una transacción sentencias del mismo DAO: lista borrada y tareas movidas
    // se escriben juntas o ninguna.
    @Transaction
    open suspend fun softDeleteMovingTasks(deleted: TaskListEntity, moved: List<TaskEntity>) {
        upsert(deleted)
        if (moved.isNotEmpty()) upsertTasks(moved)
    }
}
