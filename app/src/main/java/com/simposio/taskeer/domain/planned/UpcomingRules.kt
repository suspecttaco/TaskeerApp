package com.simposio.taskeer.domain.planned

import java.time.LocalDate
import java.time.ZoneId

sealed interface DayLabel {
    data object Today : DayLabel
    data object Tomorrow : DayLabel
    data class Weekday(val date: LocalDate) : DayLabel
}

data class UpcomingDay(val date: LocalDate, val label: DayLabel, val items: List<PlannedItem>)

data class AllTasksView(
    val today: List<PlannedItem>,
    val tomorrow: List<PlannedItem>,
    val upcoming: List<PlannedItem>,
    val someday: List<PlannedItem>,
)

enum class PlannedGroup { TODAY, TOMORROW, UPCOMING, SOMEDAY }

sealed interface AddTarget {
    data class DayOf(val date: LocalDate) : AddTarget
    data class Group(val group: PlannedGroup) : AddTarget
}

private const val UPCOMING_DAYS = 7

// Agrupa por fecha local, no por instante: una tarea de hoy con la hora pasada sigue en «hoy».
fun groupUpcoming(items: List<PlannedItem>, today: LocalDate, zone: ZoneId): List<UpcomingDay> {
    val dated = items
        .mapNotNull { item -> item.task.dueAt?.let { item to it.atZone(zone).toLocalDate() } }
        .sortedWith(compareBy({ it.first.task.dueAt }, { it.first.task.id }))
    return (0 until UPCOMING_DAYS).map { offset ->
        val date = today.plusDays(offset.toLong())
        val matching = dated.filter { (_, itemDate) -> if (offset == 0) itemDate <= date else itemDate == date }
        UpcomingDay(
            date = date,
            label = dayLabelFor(date, today),
            items = matching.map { it.first },
        )
    }
}

fun groupAllTasks(items: List<PlannedItem>, today: LocalDate, zone: ZoneId): AllTasksView {
    val dated = items
        .mapNotNull { item -> item.task.dueAt?.let { item to it.atZone(zone).toLocalDate() } }
        .sortedWith(compareBy({ it.first.task.dueAt }, { it.first.task.id }))
    val tomorrow = today.plusDays(1)
    return AllTasksView(
        today = dated.filter { it.second <= today }.map { it.first },
        tomorrow = dated.filter { it.second == tomorrow }.map { it.first },
        upcoming = dated.filter { it.second > tomorrow }.map { it.first },
        someday = items.filter { it.task.dueAt == null }
            .sortedWith(compareByDescending<PlannedItem> { it.task.createdAt }.thenBy { it.task.id }),
    )
}

fun dayLabelFor(date: LocalDate, today: LocalDate): DayLabel = when (date) {
    today -> DayLabel.Today
    today.plusDays(1) -> DayLabel.Tomorrow
    else -> DayLabel.Weekday(date)
}

fun addTargetDate(target: AddTarget, today: LocalDate): LocalDate? = when (target) {
    is AddTarget.DayOf -> target.date
    is AddTarget.Group -> when (target.group) {
        PlannedGroup.TODAY -> today
        PlannedGroup.TOMORROW -> today.plusDays(1)
        PlannedGroup.UPCOMING -> today.plusDays(2)
        PlannedGroup.SOMEDAY -> null
    }
}

fun toggleCollapsed(collapsed: Set<String>, key: String): Set<String> =
    if (key in collapsed) collapsed - key else collapsed + key
