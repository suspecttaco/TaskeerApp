package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.RepeatUnit
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.model.TaskRepeat
import java.time.DayOfWeek
import java.time.Instant

fun TaskEntity.toTask(): Task = Task(
    columnId = columnId,
    title = title,
    description = description,
    priority = TaskPriority.fromStorage(priority),
    dueAt = dueAt?.let(Instant::ofEpochMilli),
    assigneeId = assigneeId,
    position = position,
    createdAt = Instant.ofEpochMilli(createdAt),
    completedAt = completedAt?.let(Instant::ofEpochMilli),
    repeat = toRepeat(),
    repeatSuccessorId = repeatSuccessorId,
    sync = toSyncMetadata(),
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    columnId = columnId,
    title = title,
    description = description,
    priority = priority.name,
    dueAt = dueAt?.toEpochMilli(),
    assigneeId = assigneeId,
    position = position,
    createdAt = createdAt.toEpochMilli(),
    completedAt = completedAt?.toEpochMilli(),
    updatedAt = sync.updatedAt.toEpochMilli(),
    pending = sync.pending,
    deletedAt = sync.deletedAt?.toEpochMilli(),
    repeatUnit = repeat?.unit?.name,
    repeatInterval = repeat?.interval,
    repeatWeekdays = repeat?.weekdays?.let(::weekdaysToMask),
    repeatAnchorDay = repeat?.anchorDay,
    repeatSuccessorId = repeatSuccessorId,
)

// Una unidad desconocida o columnas incompletas se tratan como sin repetición, igual que el resto de enums persistidos.
private fun TaskEntity.toRepeat(): TaskRepeat? {
    val unit = repeatUnit?.let { name -> RepeatUnit.entries.firstOrNull { it.name == name } } ?: return null
    return TaskRepeat(
        unit = unit,
        interval = repeatInterval ?: return null,
        weekdays = maskToWeekdays(repeatWeekdays ?: 0),
        anchorDay = repeatAnchorDay ?: return null,
    )
}

private fun weekdaysToMask(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

private fun maskToWeekdays(mask: Int): Set<DayOfWeek> =
    DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
