package com.simposio.taskeer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ReminderDao {
    @Query("SELECT * FROM reminders WHERE taskId = :taskId AND status = 'PENDING' LIMIT 1")
    abstract fun observePendingByTask(taskId: String): Flow<ReminderEntity?>

    @Query("SELECT * FROM reminders WHERE status = 'PENDING' ORDER BY remindAt ASC, id ASC")
    abstract fun observeAllPending(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE taskId = :taskId AND status = 'PENDING' LIMIT 1")
    abstract suspend fun findPendingByTask(taskId: String): ReminderEntity?

    @Query(
        "SELECT * FROM reminders WHERE taskId = :taskId AND deletedAt IS NULL " +
            "ORDER BY createdAt DESC LIMIT 1",
    )
    abstract suspend fun findLatestByTask(taskId: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE id = :id")
    abstract suspend fun findById(id: String): ReminderEntity?

    @Query(
        "SELECT * FROM reminders WHERE status = 'PENDING' AND remindAt <= :limit " +
            "ORDER BY remindAt ASC, id ASC",
    )
    abstract suspend fun findPendingUpTo(limit: Long): List<ReminderEntity>

    @Query(
        "SELECT MIN(remindAt) FROM reminders WHERE status = 'PENDING' AND remindAt > :after",
    )
    abstract suspend fun minPendingAfter(after: Long): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(reminder: ReminderEntity)

    // Una sola sentencia condicionada a PENDING: es el cerrojo contra entregas dobles de la alarma.
    @Query(
        "UPDATE reminders SET status = 'SENT', sentAt = :now, updatedAt = :now, pending = 1 " +
            "WHERE id = :id AND status = 'PENDING'",
    )
    abstract suspend fun markSent(id: String, now: Long): Int

    @Query(
        "UPDATE reminders SET status = 'DISMISSED', updatedAt = :now, pending = 1 " +
            "WHERE id = :id AND status = 'PENDING'",
    )
    abstract suspend fun markDismissed(id: String, now: Long): Int

    // La comprobación y la inserción van en la misma transacción: dos posponer simultáneos dejan un solo pendiente.
    @Transaction
    open suspend fun insertIfNoPending(reminder: ReminderEntity): Boolean {
        if (findPendingByTask(reminder.taskId) != null) return false
        upsert(reminder)
        return true
    }
}
