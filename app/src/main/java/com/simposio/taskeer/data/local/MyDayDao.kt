package com.simposio.taskeer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Mi día muestra las pendientes y las completadas de hoy (completedAt >= dayStart); el conteo de
// vencidas y Planificado solo cuentan las no completadas. «Hoy» es una ventana pasada por parámetro:
// ninguna consulta escribe al cambiar el día, solo cambia a qué lado de dayStart cae cada marca.
@Dao
abstract class MyDayDao {
    @Query("SELECT id FROM my_day_tasks WHERE deletedAt IS NULL AND addedAt >= :dayStart")
    abstract fun observeMarkedIds(dayStart: Long): Flow<List<String>>

    @Query("SELECT * FROM my_day_tasks WHERE id = :taskId")
    abstract suspend fun findEntry(taskId: String): MyDayEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entry: MyDayEntryEntity)

    @Query(
        "SELECT t.*, b.id AS boardId, b.name AS boardName, m.addedAt AS addedAt " +
            "FROM my_day_tasks m JOIN tasks t ON t.id = m.id " +
            "LEFT JOIN columns c ON t.columnId = c.id LEFT JOIN boards b ON c.boardId = b.id " +
            "WHERE m.deletedAt IS NULL AND m.addedAt >= :dayStart AND " + VISIBLE_ANY + " AND " +
            OPEN_OR_DONE_TODAY + " " +
            "ORDER BY m.addedAt DESC, t.id ASC",
    )
    abstract fun observePinned(dayStart: Long): Flow<List<MyDayRow>>

    @Query(
        "SELECT t.*, b.id AS boardId, b.name AS boardName, NULL AS addedAt " + FROM_TASKS +
            "WHERE " + VISIBLE_ANY + " AND " + OPEN_OR_DONE_TODAY + " AND t.assigneeId = :assigneeId " +
            "AND t.dueAt IS NOT NULL AND t.dueAt < :dayEnd " +
            "ORDER BY t.dueAt ASC, t.id ASC",
    )
    abstract fun observeAssigned(assigneeId: String, dayStart: Long, dayEnd: Long): Flow<List<MyDayRow>>

    @Query(
        "SELECT t.*, b.id AS boardId, b.name AS boardName, m.addedAt AS addedAt " +
            "FROM my_day_tasks m JOIN tasks t ON t.id = m.id " +
            "LEFT JOIN columns c ON t.columnId = c.id LEFT JOIN boards b ON c.boardId = b.id " +
            "WHERE m.deletedAt IS NULL AND m.addedAt < :dayStart AND " + VISIBLE_ANY + " AND " + OPEN + " " +
            "ORDER BY m.addedAt DESC, t.id ASC",
    )
    abstract fun observeSuggestions(dayStart: Long): Flow<List<MyDayRow>>

    @Query(
        "SELECT t.*, b.id AS boardId, b.name AS boardName, NULL AS addedAt " + FROM_TASKS +
            "WHERE " + VISIBLE_ANY + " AND " + OPEN + " AND t.assigneeId = :assigneeId " +
            "AND t.dueAt IS NOT NULL " +
            "ORDER BY t.dueAt ASC, t.id ASC",
    )
    abstract fun observeDated(assigneeId: String): Flow<List<MyDayRow>>

    @Query(
        "SELECT COUNT(*) " + FROM_TASKS +
            "WHERE " + VISIBLE_ANY + " AND " + OPEN + " AND t.assigneeId = :assigneeId " +
            "AND t.dueAt IS NOT NULL AND t.dueAt < :now",
    )
    abstract fun observeOverdueCount(assigneeId: String, now: Long): Flow<Int>
}

private const val FROM_TASKS =
    "FROM tasks t LEFT JOIN columns c ON t.columnId = c.id LEFT JOIN boards b ON c.boardId = b.id "

// c.id/b.id IS NOT NULL: con LEFT JOIN, una columna inexistente pasaría el filtro de deletedAt.
private const val VISIBLE_ANY =
    "t.deletedAt IS NULL AND (t.columnId IS NULL OR " +
        "(c.id IS NOT NULL AND c.deletedAt IS NULL AND b.id IS NOT NULL AND b.deletedAt IS NULL))"

private const val OPEN = "t.completedAt IS NULL"

private const val OPEN_OR_DONE_TODAY = "(t.completedAt IS NULL OR t.completedAt >= :dayStart)"
