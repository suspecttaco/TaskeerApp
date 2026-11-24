package com.simposio.taskeer.di

import com.simposio.taskeer.domain.date.TimeZoneProvider
import java.time.DateTimeException
import java.time.ZoneId
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// current() es síncrono y se llama en rutas calientes: la elección vive en caché y la actualiza
// el flujo de preferencias.
class PreferredTimeZoneProvider(
    private val system: TimeZoneProvider,
    initialIana: String?,
) : TimeZoneProvider {

    @Volatile
    private var chosen: ZoneId? = zoneOrNull(initialIana)

    private val mutableChanges = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val changes: SharedFlow<Unit> = mutableChanges.asSharedFlow()

    override fun current(): ZoneId = chosen ?: system.current()

    fun update(ianaName: String?) {
        val next = zoneOrNull(ianaName)
        if (next != chosen) {
            chosen = next
            mutableChanges.tryEmit(Unit)
        }
    }

    private fun zoneOrNull(ianaName: String?): ZoneId? = ianaName?.let {
        try {
            ZoneId.of(it)
        } catch (_: DateTimeException) {
            null
        }
    }
}
