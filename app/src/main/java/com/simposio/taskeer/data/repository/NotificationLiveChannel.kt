package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.notification.LiveChannelState
import kotlinx.coroutines.flow.StateFlow

// Canal en vivo de notificaciones del servidor: solo mientras hay sesión y la app está en primer plano.
interface NotificationLiveChannel {
    val state: StateFlow<LiveChannelState>

    // Suspende hasta que se cancela o termina la sesión; nunca mantiene dos conexiones abiertas.
    suspend fun run()
}
