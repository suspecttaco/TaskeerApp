package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.AppNotificationDao
import com.simposio.taskeer.data.local.toAppNotification
import com.simposio.taskeer.data.local.toEntity
import com.simposio.taskeer.domain.model.AppNotification
import com.simposio.taskeer.domain.model.NotificationEvent
import com.simposio.taskeer.domain.notification.HISTORY_LIMIT
import com.simposio.taskeer.domain.notification.toNotification
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NotificationHistoryRepositoryImpl(
    private val dao: AppNotificationDao,
    private val clock: () -> Instant = Instant::now,
) : NotificationHistoryRepository {

    override fun observeAll(): Flow<List<AppNotification>> =
        dao.observeVisible().map { rows -> rows.mapNotNull { it.toAppNotification() } }

    override fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    override suspend fun record(event: NotificationEvent, delivered: Boolean) {
        val now = clock()
        dao.insertAndTrim(event.toNotification(delivered, now).toEntity(), HISTORY_LIMIT, now.toEpochMilli())
    }

    override suspend fun markRead(id: String) {
        dao.markRead(id, clock().toEpochMilli())
    }

    override suspend fun markAllRead() {
        dao.markAllRead(clock().toEpochMilli())
    }

    override suspend fun delete(id: String) {
        dao.softDelete(id, clock().toEpochMilli())
    }

    override suspend fun deleteAll() {
        dao.softDeleteAll(clock().toEpochMilli())
    }
}
