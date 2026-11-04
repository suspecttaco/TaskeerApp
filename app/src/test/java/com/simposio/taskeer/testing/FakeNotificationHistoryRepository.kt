package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.NotificationHistoryRepository
import com.simposio.taskeer.domain.model.AppNotification
import com.simposio.taskeer.domain.model.NotificationEvent
import com.simposio.taskeer.domain.model.afterLocalChange
import com.simposio.taskeer.domain.model.afterLocalDelete
import com.simposio.taskeer.domain.notification.toNotification
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeNotificationHistoryRepository(
    private val clock: () -> Instant = { Instant.EPOCH },
) : NotificationHistoryRepository {

    val all = MutableStateFlow<List<AppNotification>>(emptyList())

    private val visible: Flow<List<AppNotification>> = all.map { rows ->
        rows.filterNot { it.sync.isDeleted }
            .sortedWith(compareByDescending<AppNotification> { it.createdAt }.thenByDescending { it.id })
    }

    override fun observeAll(): Flow<List<AppNotification>> = visible

    override fun observeUnreadCount(): Flow<Int> = visible.map { rows -> rows.count { it.isUnread } }

    override suspend fun record(event: NotificationEvent, delivered: Boolean) {
        all.value = all.value + event.toNotification(delivered, clock())
    }

    fun put(notification: AppNotification) {
        all.value = all.value + notification
    }

    override suspend fun markRead(id: String) = update(id) { now ->
        if (readAt == null) copy(readAt = now, sync = sync.afterLocalChange(now)) else this
    }

    override suspend fun markAllRead() {
        all.value.forEach { if (!it.sync.isDeleted) markRead(it.id) }
    }

    override suspend fun delete(id: String) = update(id) { now ->
        copy(sync = sync.afterLocalDelete(now))
    }

    override suspend fun deleteAll() {
        all.value.forEach { delete(it.id) }
    }

    private fun update(id: String, change: AppNotification.(Instant) -> AppNotification) {
        val now = clock()
        all.value = all.value.map { if (it.id == id && !it.sync.isDeleted) it.change(now) else it }
    }
}
