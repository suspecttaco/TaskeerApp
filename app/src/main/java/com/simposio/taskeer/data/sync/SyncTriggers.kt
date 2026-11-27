package com.simposio.taskeer.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Duration

class SyncRequest(val manual: Boolean)

// Fuentes de arranque de un ciclo: conexión recuperada, primer plano, escritura (con antirrebote), reintento programado
// y «Sincronizar». Las automáticas se funden en una sola petición; la manual pasa directa y el motor ignora su retroceso.
@OptIn(FlowPreview::class)
class SyncTriggers(
    private val connectivity: Flow<Boolean>,
    private val pendingCount: Flow<Int>,
    private val scope: CoroutineScope,
    private val writeDebounce: Duration = Duration.ofSeconds(2),
    private val coalesceWindow: Duration = Duration.ofMillis(500),
) : RetryScheduler {

    private val manualChannel = Channel<Unit>(Channel.CONFLATED)
    private val foregroundChannel = Channel<Unit>(Channel.CONFLATED)
    private val retryChannel = Channel<Unit>(Channel.CONFLATED)
    private var retryJob: Job? = null

    fun manual() {
        manualChannel.trySend(Unit)
    }

    fun foreground() {
        foregroundChannel.trySend(Unit)
    }

    // Un solo reintento vivo: el más reciente sustituye al anterior.
    override fun schedule(delay: Duration) {
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(delay.toMillis())
            retryChannel.trySend(Unit)
        }
    }

    fun requests(): Flow<SyncRequest> {
        val automatic = merge(
            connectivity.distinctUntilChanged().filter { it }.map { },
            foregroundChannel.receiveAsFlow(),
            pendingCount.filter { it > 0 }.debounce(writeDebounce.toMillis()).map { },
            retryChannel.receiveAsFlow(),
        ).debounce(coalesceWindow.toMillis()).map { SyncRequest(manual = false) }
        val manual = manualChannel.receiveAsFlow().map { SyncRequest(manual = true) }
        return merge(automatic, manual).buffer(Channel.CONFLATED)
    }
}
