package com.simposio.taskeer.ui.upcoming

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.planned.DayLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun weekdayLabel(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofPattern("EEEE d MMM", locale)
        .format(date)
        .replaceFirstChar { it.titlecase(locale) }

@Composable
fun DayLabel.text(): String = when (this) {
    DayLabel.Today -> stringResource(R.string.group_today)
    DayLabel.Tomorrow -> stringResource(R.string.group_tomorrow)
    is DayLabel.Weekday -> weekdayLabel(date, LocalConfiguration.current.locales[0])
}
