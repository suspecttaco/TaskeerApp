package com.simposio.taskeer.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Solo lectura: las filas las escribe la sincronización (SyncDao).
@Dao
interface BoardAccessDao {
    @Query("SELECT effectiveRole FROM board_access WHERE boardId = :boardId")
    suspend fun roleOf(boardId: String): String?

    @Query("SELECT effectiveRole FROM board_access WHERE boardId = :boardId")
    fun observeRole(boardId: String): Flow<String?>

    @Query("SELECT workspaceId FROM board_access WHERE boardId = :boardId")
    suspend fun workspaceOf(boardId: String): String?
}
