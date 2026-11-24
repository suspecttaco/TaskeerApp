package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.date.DayWindow
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.date.dayWindowAt
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart

fun interface DayWindowSource {
    fun observe(): Flow<DayWindow>
}

// Sondea como mucho cada maxWaitMillis para notar un cambio manual de zona u hora sin registrar
// receptores de Android; es un flujo frío, así que no hay temporizadores sin suscriptores.
class ClockDayWindowSource(
    private val clock: () -> Instant,
    private val timeZone: TimeZoneProvider,
    private val maxWaitMillis: Long = DEFAULT_MAX_WAIT_MILLIS,
    private val zoneChanges: Flow<Unit> = emptyFlow(),
) : DayWindowSource {

    // Un cambio de zona elegida reinicia el bucle para emitir la ventana nueva sin esperar al sondeo.
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<DayWindow> = zoneChanges
        .onStart { emit(Unit) }
        .flatMapLatest { pollWindows() }
        .distinctUntilChanged()

    private fun pollWindows(): Flow<DayWindow> = flow {
        while (true) {
            val now = clock()
            val window = dayWindowAt(now, timeZone.current())
            emit(window)
            delay(minOf(Duration.between(now, window.end).toMillis(), maxWaitMillis).coerceAtLeast(1))
        }
    }

    private companion object {
        const val DEFAULT_MAX_WAIT_MILLIS: Long = 60_000
    }
}
