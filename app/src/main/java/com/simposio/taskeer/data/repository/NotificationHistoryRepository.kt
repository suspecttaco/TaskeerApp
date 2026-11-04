package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.model.AppNotification
import com.simposio.taskeer.domain.model.NotificationEvent
import kotlinx.coroutines.flow.Flow

interface NotificationHistoryRepository {
    fun observeAll(): Flow<List<AppNotification>>

    fun observeUnreadCount(): Flow<Int>

    suspend fun record(event: NotificationEvent, delivered: Boolean)

    suspend fun markRead(id: String)

    suspend fun markAllRead()

    suspend fun delete(id: String)

    suspend fun deleteAll()
}
