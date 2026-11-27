package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.notification.LiveChannelState
import com.simposio.taskeer.domain.notification.LiveRoleRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

// Aviso de «relee tu rol»: no lleva datos porque la notificación no dice qué rol cambió ni dónde.
interface RoleChangeSignals {
    val signals: Flow<Unit>
}

// Deriva la señal del canal en vivo existente: no abre otra conexión ni otra petición de notificaciones.
class LiveRoleChangeSignals(
    private val inbox: NotificationInboxRepository,
    private val channel: NotificationLiveChannel,
) : RoleChangeSignals {

    override val signals: Flow<Unit> = merge(
        inbox.liveArrivals.filter(LiveRoleRules::isRoleChange).map { },
        reconnections(),
    )

    // CONNECTING es de paso entre WAITING y CONNECTED: se ignora para ver la reconexión como WAITING -> CONNECTED.
    private fun reconnections(): Flow<Unit> = flow {
        var previous: LiveChannelState? = null
        channel.state.collect { current ->
            if (current == LiveChannelState.CONNECTING) return@collect
            val last = previous
            previous = current
            if (last != null && LiveRoleRules.shouldRefreshOnState(last, current)) emit(Unit)
        }
    }
}
