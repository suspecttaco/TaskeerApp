package com.simposio.taskeer.domain.myday

import java.time.LocalDate

// Un reloj o zona que retroceden dejan lastShown >= today: ni reclama ni reabre el mismo día.
fun isFirstForegroundOfDay(today: LocalDate, lastShown: LocalDate?): Boolean =
    lastShown == null || lastShown < today

fun shouldAutoOpenSuggestions(onMyDay: Boolean, count: Int, blocked: Boolean): Boolean =
    onMyDay && count > 0 && !blocked
