package com.simposio.taskeer.domain.myday

import com.simposio.taskeer.domain.model.MyDay
import com.simposio.taskeer.domain.model.MyDayTask
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.task.isOverdue
import java.time.Instant

enum class MyDaySort { DUE_DATE, PRIORITY, MARKED_AT }

enum class MyDayFilter { ALL, MARKED, ASSIGNED, OVERDUE }

data class MyDayItem(
    val task: Task,
    val boardId: String,
    val boardName: String,
    val markedAt: Instant?,
    val isAssigned: Boolean,
) {
    val isMarked: Boolean get() = markedAt != null
}

fun mergeMyDay(myDay: MyDay): List<MyDayItem> {
    val items = LinkedHashMap<String, MyDayItem>()
    myDay.pinned.forEach { items[it.task.id] = it.toItem(isAssigned = false) }
    myDay.assigned.forEach { assigned ->
        val existing = items[assigned.task.id]
        items[assigned.task.id] = existing?.copy(isAssigned = true) ?: assigned.toItem(isAssigned = true)
    }
    return items.values.toList()
}

// Mismo criterio que el conteo de vencidas: solo cuentan las asignadas.
fun MyDayItem.isOverdueAssigned(now: Instant): Boolean =
    isAssigned && isOverdue(task.dueAt, isDoneColumn = false, now = now)

fun filterMyDay(items: List<MyDayItem>, filter: MyDayFilter, now: Instant): List<MyDayItem> =
    when (filter) {
        MyDayFilter.ALL -> items
        MyDayFilter.MARKED -> items.filter { it.isMarked }
        MyDayFilter.ASSIGNED -> items.filter { it.isAssigned }
        MyDayFilter.OVERDUE -> items.filter { it.isOverdueAssigned(now) }
    }

fun sortMyDay(items: List<MyDayItem>, sort: MyDaySort): List<MyDayItem> {
    val byDueNullsLast = compareBy<MyDayItem, Instant?>(nullsLast()) { it.task.dueAt }
    val comparator = when (sort) {
        MyDaySort.DUE_DATE -> byDueNullsLast
        MyDaySort.PRIORITY -> compareByDescending<MyDayItem> { it.task.priority.rank() }.then(byDueNullsLast)
        MyDaySort.MARKED_AT -> compareBy<MyDayItem, Instant?>(nullsLast(reverseOrder())) { it.markedAt }
    }
    return items.sortedWith(comparator.thenBy { it.task.id })
}

fun applyMyDay(items: List<MyDayItem>, filter: MyDayFilter, sort: MyDaySort, now: Instant): List<MyDayItem> =
    sortMyDay(filterMyDay(items, filter, now), sort)

private fun MyDayTask.toItem(isAssigned: Boolean) = MyDayItem(
    task = task,
    boardId = boardId,
    boardName = boardName,
    markedAt = addedAt,
    isAssigned = isAssigned,
)

private fun TaskPriority.rank(): Int = when (this) {
    TaskPriority.LOW -> 0
    TaskPriority.MEDIUM -> 1
    TaskPriority.HIGH -> 2
    TaskPriority.URGENT -> 3
}
