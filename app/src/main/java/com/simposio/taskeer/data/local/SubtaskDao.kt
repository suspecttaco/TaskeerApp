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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(subtask: SubtaskEntity)
}
