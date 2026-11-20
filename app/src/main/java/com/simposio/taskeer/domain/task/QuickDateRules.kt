package com.simposio.taskeer.domain.task

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class QuickDateShortcut { TOMORROW_MORNING, NEXT_WEEK, SOMEDAY }

sealed interface QuickDateChoice {
    data object None : QuickDateChoice

    data class Shortcut(val shortcut: QuickDateShortcut) : QuickDateChoice

    data class Picked(val date: LocalDate) : QuickDateChoice
}

// Constante propia aunque hoy valga como DEFAULT_DUE_TIME: «por la mañana» no debe moverse si cambia la hora por defecto.
val MORNING_TIME: LocalTime = LocalTime.of(9, 0)

// La intención se resuelve al enviar: un atajo elegido antes de medianoche no debe quedar fijo en el día anterior.
fun quickDueFor(choice: QuickDateChoice, today: LocalDate): LocalDateTime? = when (choice) {
    QuickDateChoice.None -> null
    is QuickDateChoice.Picked -> dueForQuickAdd(choice.date, null)
    is QuickDateChoice.Shortcut -> when (choice.shortcut) {
        QuickDateShortcut.TOMORROW_MORNING -> today.plusDays(1).atTime(MORNING_TIME)
        QuickDateShortcut.NEXT_WEEK -> rescheduledDue(null, RescheduleTarget.NEXT_WEEK, today)
        QuickDateShortcut.SOMEDAY -> null
    }
}

fun quickDateOf(choice: QuickDateChoice, today: LocalDate): LocalDate? = quickDueFor(choice, today)?.toLocalDate()

fun toggled(choice: QuickDateChoice, shortcut: QuickDateShortcut): QuickDateChoice =
    if (choice == QuickDateChoice.Shortcut(shortcut)) QuickDateChoice.None else QuickDateChoice.Shortcut(shortcut)
