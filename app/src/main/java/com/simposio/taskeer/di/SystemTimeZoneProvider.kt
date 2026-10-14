package com.simposio.taskeer.di

import com.simposio.taskeer.domain.date.TimeZoneProvider
import java.time.ZoneId

object SystemTimeZoneProvider : TimeZoneProvider {
    override fun current(): ZoneId = ZoneId.systemDefault()
}
