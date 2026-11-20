package com.simposio.taskeer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Mi día muestra las pendientes y las completadas de hoy (completedAt >= dayStart); el conteo de
// vencidas y las vistas por fecha solo cuentan las no completadas. «Hoy» es una ventana pasada por parámetro:
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
        "SELECT t.*, b.id AS boardId, b.name AS boardName, l.name AS listName, m.addedAt AS addedAt " +
            "FROM my_day_tasks m JOIN tasks t ON t.id = m.id " +
            "LEFT JOIN columns c ON t.columnId = c.id LEFT JOIN boards b ON c.boardId = b.id " +
            "LEFT JOIN task_lists l ON t.listId = l.id " +
            "WHERE m.deletedAt IS NULL AND m.addedAt >= :dayStart AND " + VISIBLE_ANY + " AND " +
            OPEN_OR_DONE_TODAY + " " +
            "ORDER BY m.addedAt DESC, t.id ASC",
    )
    abstract fun observePinned(dayStart: Long): Flow<List<MyDayRow>>

    @Query(
        "SELECT t.*, b.id AS boardId, b.name AS boardName, l.name AS listName, " +
            "CASE WHEN m.deletedAt IS NULL THEN m.addedAt END AS addedAt " + SUGGESTION_FROM_WHERE + " " +
            "ORDER BY " + SUGGESTION_RANK + " ASC, " +
            "CASE WHEN " + SUGGESTION_RANK + " = 2 THEN -m.addedAt END ASC, " +
            "(t.dueAt IS NULL) ASC, t.dueAt ASC, " +
            "CASE WHEN " + SUGGESTION_RANK + " = 3 AND t.dueAt IS NULL THEN -t.updatedAt END ASC, t.id ASC",
    )
    abstract fun observeSuggestions(assigneeId: String, dayStart: Long, dayEnd: Long): Flow<List<MyDayRow>>

    @Query("SELECT COUNT(*) " + SUGGESTION_FROM_WHERE)
    abstract fun observeSuggestionCount(assigneeId: String, dayStart: Long): Flow<Int>

    @Query(
        "SELECT t.*, b.id AS boardId, b.name AS boardName, l.name AS listName, NULL AS addedAt " + FROM_TASKS +
            "WHERE " + VISIBLE_ANY + " AND " + OPEN + " AND t.assigneeId = :assigneeId " +
            "ORDER BY t.dueAt ASC, t.id ASC",
    )
    abstract fun observeOpen(assigneeId: String): Flow<List<MyDayRow>>

    @Query(
        "SELECT COUNT(*) " + FROM_TASKS +
            "WHERE " + VISIBLE_ANY + " AND " + OPEN + " AND t.assigneeId = :assigneeId " +
            "AND t.dueAt IS NOT NULL AND t.dueAt < :now",
    )
    abstract fun observeOverdueCount(assigneeId: String, now: Long): Flow<Int>
}

private const val FROM_TASKS =
    "FROM tasks t LEFT JOIN columns c ON t.columnId = c.id LEFT JOIN boards b ON c.boardId = b.id " +
        "LEFT JOIN task_lists l ON t.listId = l.id "

// c.id/b.id IS NOT NULL: con LEFT JOIN, una columna inexistente pasaría el filtro de deletedAt.
private const val VISIBLE_ANY =
    "t.deletedAt IS NULL AND (t.columnId IS NULL OR " +
        "(c.id IS NOT NULL AND c.deletedAt IS NULL AND b.id IS NOT NULL AND b.deletedAt IS NULL))"

private const val OPEN = "t.completedAt IS NULL"

private const val OPEN_OR_DONE_TODAY = "(t.completedAt IS NULL OR t.completedAt >= :dayStart)"

// Sugerencia = abierta, visible, no marcada hoy, no descartada hoy y (asignada a «Yo», con marca anterior o
// quitada hoy). Descartada = fila borrada con addedAt = DISMISSED_ADDED_AT; quitada = fila borrada con su addedAt real.
// Cada cláusula empieza por m.id IS NULL porque el LEFT JOIN deja m.* a NULL sin fila.
private const val SUGGESTION_FROM_WHERE =
    FROM_TASKS + "LEFT JOIN my_day_tasks m ON m.id = t.id " +
        "WHERE " + VISIBLE_ANY + " AND " + OPEN + " " +
        "AND (m.id IS NULL OR m.deletedAt IS NOT NULL OR m.addedAt < :dayStart) " +
        "AND (m.id IS NULL OR m.deletedAt IS NULL OR m.addedAt <> $DISMISSED_ADDED_AT OR m.deletedAt < :dayStart) " +
        "AND (t.assigneeId = :assigneeId OR (m.id IS NOT NULL AND m.deletedAt IS NULL) " +
        "OR (m.id IS NOT NULL AND m.addedAt <> $DISMISSED_ADDED_AT AND m.deletedAt >= :dayStart))"

private const val SUGGESTION_RANK =
    "(CASE WHEN t.dueAt IS NOT NULL AND t.dueAt < :dayStart THEN 0 " +
        "WHEN t.dueAt IS NOT NULL AND t.dueAt < :dayEnd THEN 1 " +
        "WHEN m.id IS NOT NULL AND m.deletedAt IS NULL THEN 2 ELSE 3 END)"
