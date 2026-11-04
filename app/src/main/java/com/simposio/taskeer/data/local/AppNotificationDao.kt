package com.simposio.taskeer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AppNotificationDao {
    @Query(
        "SELECT * FROM app_notifications WHERE deletedAt IS NULL " +
            "ORDER BY createdAt DESC, id DESC",
    )
    abstract fun observeVisible(): Flow<List<AppNotificationEntity>>

    @Query(
        "SELECT COUNT(*) FROM app_notifications WHERE deletedAt IS NULL AND readAt IS NULL",
    )
    abstract fun observeUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entity: AppNotificationEntity)

    @Query(
        "UPDATE app_notifications SET readAt = :now, updatedAt = :now, pending = 1 " +
            "WHERE id = :id AND readAt IS NULL AND deletedAt IS NULL",
    )
    abstract suspend fun markRead(id: String, now: Long): Int

    @Query(
        "UPDATE app_notifications SET readAt = :now, updatedAt = :now, pending = 1 " +
            "WHERE readAt IS NULL AND deletedAt IS NULL",
    )
    abstract suspend fun markAllRead(now: Long): Int

    @Query(
        "UPDATE app_notifications SET deletedAt = :now, updatedAt = :now, pending = 1 " +
            "WHERE id = :id AND deletedAt IS NULL",
    )
    abstract suspend fun softDelete(id: String, now: Long): Int

    @Query(
        "UPDATE app_notifications SET deletedAt = :now, updatedAt = :now, pending = 1 " +
            "WHERE deletedAt IS NULL",
    )
    abstract suspend fun softDeleteAll(now: Long): Int

    @Query(
        "UPDATE app_notifications SET deletedAt = :now, updatedAt = :now, pending = 1 " +
            "WHERE deletedAt IS NULL AND id != :keepId AND id NOT IN (" +
            "SELECT id FROM app_notifications WHERE deletedAt IS NULL AND id != :keepId " +
            "ORDER BY createdAt DESC, id DESC LIMIT :keepOthers)",
    )
    abstract suspend fun trimTo(keepId: String, keepOthers: Int, now: Long): Int

    // Room solo envuelve en una transacción sentencias del mismo DAO: insertar y recortar van juntas.
    @Transaction
    open suspend fun insertAndTrim(entity: AppNotificationEntity, keep: Int, now: Long) {
        insert(entity)
        trimTo(entity.id, keep - 1, now)
    }
}
