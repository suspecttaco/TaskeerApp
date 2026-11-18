package com.simposio.taskeer.domain.planned

import com.simposio.taskeer.domain.date.DayWindow
import com.simposio.taskeer.domain.model.Task
import java.time.LocalDate
import java.time.ZoneId

data class PlannedItem(val task: Task, val boardId: String?, val boardName: String?, val listName: String? = null)

data class PlannedDay(val date: LocalDate, val items: List<PlannedItem>)

data class PlannedView(
    val overdue: List<PlannedItem>,
    val today: List<PlannedItem>,
    val tomorrow: List<PlannedItem>,
    val upcoming: List<PlannedDay>,
)

// Agrupa por fecha local, no por instante: una tarea de hoy con la hora pasada sigue en «hoy».
fun groupPlanned(items: List<PlannedItem>, window: DayWindow, zone: ZoneId): PlannedView {
    val dated = items
        .mapNotNull { item -> item.task.dueAt?.let { item to it.atZone(zone).toLocalDate() } }
        .sortedWith(compareBy({ it.first.task.dueAt }, { it.first.task.id }))
    val tomorrow = window.date.plusDays(1)
    return PlannedView(
        overdue = dated.filter { it.second < window.date }.map { it.first },
        today = dated.filter { it.second == window.date }.map { it.first },
        tomorrow = dated.filter { it.second == tomorrow }.map { it.first },
        upcoming = dated.filter { it.second > tomorrow }
            .groupBy({ it.second }, { it.first })
            .toSortedMap()
            .map { (date, group) -> PlannedDay(date, group) },
    )
}
