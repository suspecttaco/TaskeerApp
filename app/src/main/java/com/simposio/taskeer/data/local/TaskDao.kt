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

    @Query("SELECT * FROM tasks WHERE columnId IS NULL AND deletedAt IS NULL ORDER BY createdAt DESC, id ASC")
    abstract fun observeInbox(): Flow<List<TaskEntity>>

    @Query("SELECT COUNT(*) FROM tasks WHERE columnId IS NULL AND deletedAt IS NULL")
    abstract suspend fun countInbox(): Int

    @Query(
        "SELECT columnId AS columnId, COUNT(*) AS count FROM tasks " +
            "WHERE deletedAt IS NULL AND columnId IS NOT NULL GROUP BY columnId",
    )
    abstract fun observeCountsByColumn(): Flow<List<ColumnCountRow>>

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

    @Query(
        "UPDATE reminders SET status = 'DISMISSED', updatedAt = :now, pending = 1 " +
            "WHERE taskId = :taskId AND status = 'PENDING'",
    )
    abstract suspend fun dismissRemindersOfTask(taskId: String, now: Long): Int

    @Query(
        "SELECT tasks.id AS id, tasks.title AS title, tasks.dueAt AS dueAt FROM tasks " +
            "LEFT JOIN columns ON tasks.columnId = columns.id " +
            "WHERE tasks.dueAt IS NOT NULL AND tasks.deletedAt IS NULL " +
            "AND (tasks.columnId IS NULL OR columns.isDoneColumn = 0) ORDER BY tasks.dueAt ASC, tasks.id ASC",
    )
    abstract fun observeReminderCandidates(): Flow<List<ReminderCandidateRow>>

    @Query(
        "SELECT COUNT(*) FROM tasks LEFT JOIN columns ON tasks.columnId = columns.id " +
            "WHERE tasks.deletedAt IS NULL AND (tasks.columnId IS NULL OR columns.isDoneColumn = 0)",
    )
    abstract suspend fun countActive(): Int

    @Query(
        "SELECT tasks.title AS title, tasks.deletedAt IS NOT NULL AS deleted, " +
            "CASE WHEN tasks.columnId IS NULL THEN 0 ELSE COALESCE(columns.isDoneColumn, 0) END " +
            "AS completed FROM tasks " +
            "LEFT JOIN columns ON tasks.columnId = columns.id WHERE tasks.id = :taskId",
    )
    abstract suspend fun findReminderState(taskId: String): ReminderTaskStateRow?

    // Room solo envuelve en una transacción sentencias del mismo DAO: por eso la cascada vive aquí.
    @Transaction
    open suspend fun upsertDeletedWithSubtasks(task: TaskEntity, deletedAt: Long) {
        upsert(task)
        softDeleteSubtasksOfTask(task.id, deletedAt)
        dismissRemindersOfTask(task.id, deletedAt)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE, entity = SubtaskEntity::class)
    abstract suspend fun insertSubtasks(subtasks: List<SubtaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE, entity = ReminderEntity::class)
    abstract suspend fun insertReminder(reminder: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE, entity = MyDayEntryEntity::class)
    abstract suspend fun insertMyDayEntry(entry: MyDayEntryEntity)

    @Transaction
    open suspend fun insertTaskWithExtras(
        task: TaskEntity,
        subtasks: List<SubtaskEntity>,
        reminder: ReminderEntity?,
        myDayEntry: MyDayEntryEntity?,
    ) {
        upsert(task)
        if (subtasks.isNotEmpty()) insertSubtasks(subtasks)
        reminder?.let { insertReminder(it) }
        myDayEntry?.let { insertMyDayEntry(it) }
    }
}
