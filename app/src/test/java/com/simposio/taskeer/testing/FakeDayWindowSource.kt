package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.DayWindowSource
import com.simposio.taskeer.domain.date.DayWindow
import com.simposio.taskeer.domain.date.dayWindowAt
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeDayWindowSource(now: Instant, zone: ZoneId) : DayWindowSource {
    val window = MutableStateFlow(dayWindowAt(now, zone))

    fun advanceTo(now: Instant, zone: ZoneId) {
        window.value = dayWindowAt(now, zone)
    }

    override fun observe(): Flow<DayWindow> = window
}
