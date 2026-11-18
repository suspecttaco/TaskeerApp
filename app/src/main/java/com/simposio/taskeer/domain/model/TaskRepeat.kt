package com.simposio.taskeer.domain.model

import java.time.DayOfWeek

enum class RepeatUnit { DAY, WEEK, MONTH, YEAR }

data class TaskRepeat(
    val unit: RepeatUnit,
    val interval: Int,
    // Solo con WEEK; vacío significa «el mismo día de la semana que la fecha límite».
    val weekdays: Set<DayOfWeek> = emptySet(),
    // Día del mes que MONTH y YEAR intentan respetar: evita que un recorte (31 -> 28) se arrastre.
    val anchorDay: Int,
)
