package com.simposio.taskeer.data.sync

import com.simposio.taskeer.data.local.SubtaskEntity
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.data.local.TaskListEntity
import com.simposio.taskeer.domain.sync.AssigneeMapping
import com.simposio.taskeer.domain.sync.SyncTimestamps

// El registro del servidor sustituye a la fila local. Una clave ausente conserva el valor local: el contrato permite
// registros parciales en la respuesta de un envío y la fila nunca debe perder un campo por no venir.

private inline fun <T> Map<String, Any?>.orKeep(key: String, current: T, read: (Any?) -> T): T =
    if (containsKey(key)) read(get(key)) else current

private fun Any?.millis(): Long? = (this as? String)?.let(SyncTimestamps::parse)?.toEpochMilli()

private fun Any?.intOrNull(): Int? = (this as? Number)?.toInt()

private fun ServerRecord.stamp(): Pair<Long, Long?> = updatedAt.toEpochMilli() to deletedAt?.toEpochMilli()

internal fun TaskListEntity.adoptedFrom(record: ServerRecord): TaskListEntity {
    val (updated, deleted) = record.stamp()
    val f = record.fields
    return copy(
        name = f.orKeep("name", name) { it as? String ?: name },
        position = f.orKeep("position", position) { it.intOrNull() ?: position },
        updatedAt = updated,
        deletedAt = deleted,
        pending = false,
    )
}

internal fun TaskEntity.adoptedFrom(record: ServerRecord, me: String): TaskEntity {
    val (updated, deleted) = record.stamp()
    val f = record.fields
    return copy(
        listId = f.orKeep("listId", listId) { it as? String },
        columnId = f.orKeep("columnId", columnId) { it as? String },
        title = f.orKeep("title", title) { it as? String ?: title },
        description = f.orKeep("description", description) { it as? String },
        priority = f.orKeep("priority", priority) { it as? String ?: priority },
        dueAt = f.orKeep("dueAt", dueAt) { it.millis() },
        assigneeId = f.orKeep("assigneeId", assigneeId) { AssigneeMapping.toLocal(it as? String, me) },
        position = f.orKeep("position", position) { it.intOrNull() ?: position },
        completedAt = f.orKeep("completedAt", completedAt) { it.millis() },
        repeatUnit = f.orKeep("repeatUnit", repeatUnit) { it as? String },
        repeatInterval = f.orKeep("repeatInterval", repeatInterval) { it.intOrNull() },
        repeatWeekdays = f.orKeep("repeatWeekdays", repeatWeekdays) { it.intOrNull() },
        repeatAnchorDay = f.orKeep("repeatAnchorDay", repeatAnchorDay) { it.intOrNull() },
        repeatSuccessorId = f.orKeep("repeatSuccessorId", repeatSuccessorId) { it as? String },
        updatedAt = updated,
        deletedAt = deleted,
        pending = false,
    )
}

internal fun SubtaskEntity.adoptedFrom(record: ServerRecord, me: String): SubtaskEntity {
    val (updated, deleted) = record.stamp()
    val f = record.fields
    return copy(
        taskId = f.orKeep("taskId", taskId) { it as? String ?: taskId },
        title = f.orKeep("title", title) { it as? String ?: title },
        completed = f.orKeep("completed", completed) { it as? Boolean ?: completed },
        position = f.orKeep("position", position) { it.intOrNull() ?: position },
        assigneeId = f.orKeep("assigneeId", assigneeId) { AssigneeMapping.toLocal(it as? String, me) },
        dueAt = f.orKeep("dueAt", dueAt) { it.millis() },
        updatedAt = updated,
        deletedAt = deleted,
        pending = false,
    )
}
