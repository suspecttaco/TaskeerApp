package com.simposio.taskeer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Las tareas de columnas de cierre (c.isDoneColumn = 0) no aparecen en Mi día ni en el
// conteo de vencidas; el filtro vive en SQL para que bloques y conteo no diverjan.
@Dao
abstract class MyDayDao {
    @Query("SELECT id FROM my_day_tasks WHERE deletedAt IS NULL")
    abstract fun observeMarkedIds(): Flow<List<String>>

    @Query("SELECT * FROM my_day_tasks WHERE id = :taskId")
    abstract suspend fun findEntry(taskId: String): MyDayEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entry: MyDayEntryEntity)

    @Query(
        "SELECT t.*, b.id AS boardId, b.name AS boardName, m.addedAt AS addedAt " +
            "FROM my_day_tasks m JOIN tasks t ON t.id = m.id " +
            "LEFT JOIN columns c ON t.columnId = c.id LEFT JOIN boards b ON c.boardId = b.id " +
            "WHERE m.deletedAt IS NULL AND " + VISIBLE + " " +
            "ORDER BY m.addedAt DESC, t.id ASC",
    )
    abstract fun observePinned(): Flow<List<MyDayRow>>

    @Query(
        "SELECT t.*, b.id AS boardId, b.name AS boardName, NULL AS addedAt " + FROM_TASKS +
            "WHERE " + VISIBLE + " AND t.assigneeId = :assigneeId AND t.dueAt IS NOT NULL " +
            "ORDER BY t.dueAt ASC, t.id ASC",
    )
    abstract fun observeAssigned(assigneeId: String): Flow<List<MyDayRow>>

    @Query(
        "SELECT COUNT(*) " + FROM_TASKS +
            "WHERE " + VISIBLE + " AND t.assigneeId = :assigneeId AND t.dueAt IS NOT NULL " +
            "AND t.dueAt < :now",
    )
    abstract fun observeOverdueCount(assigneeId: String, now: Long): Flow<Int>
}

private const val FROM_TASKS =
    "FROM tasks t LEFT JOIN columns c ON t.columnId = c.id LEFT JOIN boards b ON c.boardId = b.id "

// c.id/b.id IS NOT NULL: con LEFT JOIN, una columna inexistente pasaría el filtro de deletedAt.
private const val VISIBLE =
    "t.deletedAt IS NULL AND (t.columnId IS NULL OR " +
        "(c.id IS NOT NULL AND c.deletedAt IS NULL AND b.id IS NOT NULL AND b.deletedAt IS NULL " +
        "AND c.isDoneColumn = 0))"
