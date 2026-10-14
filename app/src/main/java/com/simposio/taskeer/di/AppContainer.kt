package com.simposio.taskeer.di

import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider

class AppContainer(
    val preferencesRepository: PreferencesRepository,
    val timeZoneProvider: TimeZoneProvider,
) {
    val dateTimeConverter: DateTimeConverter = DateTimeConverter(timeZoneProvider)
}
