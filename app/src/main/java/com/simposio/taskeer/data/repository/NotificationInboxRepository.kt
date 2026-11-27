package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.notification.InboxOutcome
import com.simposio.taskeer.domain.notification.InboxState
import com.simposio.taskeer.domain.notification.ServerNotification
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

// Bandeja de notificaciones del servidor: estado solo en memoria, sin persistencia local.
interface NotificationInboxRepository {
    val state: StateFlow<InboxState>

    // Solo lo que `applyLive` acepta: sin duplicadas, sin borradas localmente y con sesión. No transporta nada más que la notificación.
    val liveArrivals: SharedFlow<ServerNotification>

    suspend fun refreshUnread(): InboxOutcome

    // Puesta al día tras abrir el canal en vivo: como `refreshUnread`, pero sin respetar el intervalo mínimo.
    suspend fun catchUp(): InboxOutcome

    // Incorpora una notificación empujada por el canal en vivo; ignora duplicados, borradas y sin sesión.
    fun applyLive(notification: ServerNotification)

    suspend fun refresh(): InboxOutcome

    suspend fun markRead(id: String): InboxOutcome

    suspend fun markAllRead(): InboxOutcome

    suspend fun delete(id: String): InboxOutcome

    suspend fun deleteRead(): InboxOutcome
}
