package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.notification.InboxOutcome
import com.simposio.taskeer.domain.notification.InboxState
import kotlinx.coroutines.flow.StateFlow

// Bandeja de notificaciones del servidor: estado solo en memoria, sin persistencia local.
interface NotificationInboxRepository {
    val state: StateFlow<InboxState>

    suspend fun refreshUnread(): InboxOutcome

    suspend fun refresh(): InboxOutcome

    suspend fun markRead(id: String): InboxOutcome

    suspend fun markAllRead(): InboxOutcome

    suspend fun delete(id: String): InboxOutcome

    suspend fun deleteRead(): InboxOutcome
}
