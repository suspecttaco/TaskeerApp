package com.simposio.taskeer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardDao {
    @Query("SELECT * FROM boards WHERE deletedAt IS NULL")
    fun observeActive(): Flow<List<BoardEntity>>

    @Query("SELECT * FROM boards WHERE id = :id")
    suspend fun findById(id: String): BoardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(board: BoardEntity)
}
