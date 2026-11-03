package com.simposio.taskeer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SubtaskDao {
    @Query(
        "SELECT * FROM subtasks WHERE taskId = :taskId AND deletedAt IS NULL " +
            "ORDER BY position ASC, id ASC",
    )
    abstract fun observeByTask(taskId: String): Flow<List<SubtaskEntity>>

    @Query(
        "SELECT * FROM subtasks WHERE taskId = :taskId AND deletedAt IS NULL " +
            "ORDER BY position ASC, id ASC",
    )
    abstract suspend fun findActiveByTask(taskId: String): List<SubtaskEntity>

    @Query("SELECT * FROM subtasks WHERE id = :id")
    abstract suspend fun findById(id: String): SubtaskEntity?

    @Query("SELECT COUNT(*) FROM subtasks WHERE taskId = :taskId AND deletedAt IS NULL")
    abstract fun observeTotalCount(taskId: String): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM subtasks WHERE taskId = :taskId AND deletedAt IS NULL " +
            "AND completed = 1",
    )
    abstract fun observeCompletedCount(taskId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(subtask: SubtaskEntity)
}
