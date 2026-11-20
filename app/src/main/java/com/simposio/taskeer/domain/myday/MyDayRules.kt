package com.simposio.taskeer.domain.myday

import com.simposio.taskeer.domain.model.MyDay
import com.simposio.taskeer.domain.model.MyDayTask
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import java.time.Instant

enum class MyDaySort { DUE_DATE, PRIORITY, MARKED_AT }

data class MyDayItem(
    val task: Task,
    val boardId: String?,
    val boardName: String?,
    val markedAt: Instant?,
    val listName: String? = null,
) {
    val isMarked: Boolean get() = markedAt != null
}

fun mergeMyDay(myDay: MyDay): List<MyDayItem> = myDay.pinned.map { it.toItem() }

fun sortMyDay(items: List<MyDayItem>, sort: MyDaySort): List<MyDayItem> {
    val byDueNullsLast = compareBy<MyDayItem, Instant?>(nullsLast()) { it.task.dueAt }
    val comparator = when (sort) {
        MyDaySort.DUE_DATE -> byDueNullsLast
        MyDaySort.PRIORITY -> compareByDescending<MyDayItem> { it.task.priority.rank() }.then(byDueNullsLast)
        MyDaySort.MARKED_AT -> compareBy<MyDayItem, Instant?>(nullsLast(reverseOrder())) { it.markedAt }
    }
    val (done, pending) = items.sortedWith(comparator.thenBy { it.task.id }).partition { it.task.isCompleted }
    return pending + done
}

fun applyMyDay(items: List<MyDayItem>, sort: MyDaySort): List<MyDayItem> = sortMyDay(items, sort)

private fun MyDayTask.toItem() = MyDayItem(
    task = task,
    boardId = boardId,
    boardName = boardName,
    markedAt = addedAt,
    listName = listName,
)

private fun TaskPriority.rank(): Int = when (this) {
    TaskPriority.LOW -> 0
    TaskPriority.MEDIUM -> 1
    TaskPriority.HIGH -> 2
    TaskPriority.URGENT -> 3
}
