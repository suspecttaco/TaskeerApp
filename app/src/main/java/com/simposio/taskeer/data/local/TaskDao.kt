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

    @Query("SELECT * FROM tasks WHERE listId = :listId AND deletedAt IS NULL ORDER BY createdAt DESC, id ASC")
    abstract fun observeByList(listId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE listId = :listId AND deletedAt IS NULL")
    abstract suspend fun findActiveByList(listId: String): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE listId = :listId AND deletedAt IS NULL")
    abstract suspend fun countInList(listId: String): Int

    // Solo pendientes: el contador de una lista es «N tareas por hacer». observeCountsByColumn, en
    // cambio, cuenta también las completadas porque el WIP y la columna de cierre las incluyen.
    @Query(
        "SELECT listId AS listId, COUNT(*) AS count FROM tasks " +
            "WHERE deletedAt IS NULL AND completedAt IS NULL AND listId IS NOT NULL GROUP BY listId",
    )
    abstract fun observeActiveCountsByList(): Flow<List<ListCountRow>>

    @Query(
        "SELECT columnId AS columnId, COUNT(*) AS count FROM tasks " +
            "WHERE deletedAt IS NULL AND columnId IS NOT NULL GROUP BY columnId",
    )
    abstract fun observeCountsByColumn(): Flow<List<ColumnCountRow>>

    // Sin filtro de completedAt: la búsqueda incluye las completadas.
    @Query(
        "SELECT t.*, b.id AS boardId, c.name AS columnName, b.name AS boardName, l.name AS listName " +
            FROM_TASKS + "WHERE " + VISIBLE_ANY + " ORDER BY t.id ASC",
    )
    abstract fun observeSearchable(): Flow<List<SearchTaskRow>>

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
            "WHERE tasks.dueAt IS NOT NULL AND tasks.deletedAt IS NULL " +
            "AND tasks.completedAt IS NULL ORDER BY tasks.dueAt ASC, tasks.id ASC",
    )
    abstract fun observeReminderCandidates(): Flow<List<ReminderCandidateRow>>

    @Query(
        "SELECT COUNT(*) FROM tasks WHERE tasks.deletedAt IS NULL AND tasks.completedAt IS NULL",
    )
    abstract suspend fun countActive(): Int

    @Query(
        "SELECT tasks.title AS title, tasks.deletedAt IS NOT NULL AS deleted, " +
            "tasks.completedAt IS NOT NULL AS completed FROM tasks WHERE tasks.id = :taskId",
    )
    abstract suspend fun findReminderState(taskId: String): ReminderTaskStateRow?

    // Room solo envuelve en una transacción sentencias del mismo DAO: por eso la cascada vive aquí.
    @Transaction
    open suspend fun upsertDeletedWithSubtasks(task: TaskEntity, deletedAt: Long) {
        upsert(task)
        softDeleteSubtasksOfTask(task.id, deletedAt)
        dismissRemindersOfTask(task.id, deletedAt)
    }

    @Query("SELECT COUNT(*) FROM tasks WHERE completedAt IS NOT NULL AND deletedAt IS NULL")
    abstract fun observeCompletedCount(): Flow<Int>

    @Query("SELECT * FROM tasks WHERE completedAt IS NOT NULL AND deletedAt IS NULL ORDER BY id ASC")
    abstract suspend fun findCompleted(): List<TaskEntity>

    // Reutiliza la cascada de deleteTask (subtareas y recordatorios) para que ambos caminos no diverjan.
    @Transaction
    open suspend fun softDeleteCompleted(deletedAt: Long): List<String> {
        val completed = findCompleted()
        completed.forEach { task ->
            upsertDeletedWithSubtasks(task.copy(deletedAt = deletedAt, updatedAt = deletedAt, pending = true), deletedAt)
        }
        return completed.map { it.id }
    }

    // deletedAt = :deletedAt aísla las subtareas que borró este mismo borrado de las borradas antes.
    @Query(
        "UPDATE subtasks SET deletedAt = NULL, updatedAt = :now, pending = 1 " +
            "WHERE taskId = :taskId AND deletedAt = :deletedAt",
    )
    abstract suspend fun restoreSubtasksOfTask(taskId: String, deletedAt: Long, now: Long)

    // El borrado escribió el mismo milisegundo en updatedAt del recordatorio; remindAt > now evita una alarma inmediata.
    @Query(
        "UPDATE reminders SET status = 'PENDING', updatedAt = :now, pending = 1 " +
            "WHERE taskId = :taskId AND status = 'DISMISSED' AND updatedAt = :deletedAt AND remindAt > :now",
    )
    abstract suspend fun restoreRemindersOfTask(taskId: String, deletedAt: Long, now: Long): Int

    @Transaction
    open suspend fun upsertRestoredWithSubtasks(tasks: List<TaskEntity>, deletedAtById: Map<String, Long>, now: Long) {
        upsertAll(tasks)
        tasks.forEach { task ->
            val deletedAt = deletedAtById.getValue(task.id)
            restoreSubtasksOfTask(task.id, deletedAt, now)
            restoreRemindersOfTask(task.id, deletedAt, now)
        }
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

    // Original completada, renumeradas y sucesora comparten transacción: o se escriben todas o ninguna.
    @Transaction
    open suspend fun upsertAllWithSuccessor(
        tasks: List<TaskEntity>,
        successor: TaskEntity,
        subtasks: List<SubtaskEntity>,
        reminder: ReminderEntity?,
    ) {
        upsertAll(tasks)
        upsert(successor)
        if (subtasks.isNotEmpty()) insertSubtasks(subtasks)
        reminder?.let { insertReminder(it) }
    }
}
