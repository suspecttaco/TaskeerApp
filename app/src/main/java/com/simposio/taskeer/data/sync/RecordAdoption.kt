package com.simposio.taskeer.data.sync

import com.simposio.taskeer.data.local.BoardEntity
import com.simposio.taskeer.data.local.ColumnEntity
import com.simposio.taskeer.data.local.SubtaskEntity
import com.simposio.taskeer.data.local.SyncableEntity
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.data.local.TaskListEntity
import com.simposio.taskeer.domain.sync.AssigneeMapping
import com.simposio.taskeer.domain.sync.SyncEntity
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

internal fun BoardEntity.adoptedFrom(record: ServerRecord): BoardEntity {
    val (updated, deleted) = record.stamp()
    val f = record.fields
    return copy(
        name = f.orKeep("name", name) { it as? String ?: name },
        workspaceId = f.orKeep("workspaceId", workspaceId) { it as? String ?: workspaceId },
        updatedAt = updated,
        deletedAt = deleted,
        pending = false,
    )
}

internal fun ColumnEntity.adoptedFrom(record: ServerRecord): ColumnEntity {
    val (updated, deleted) = record.stamp()
    val f = record.fields
    return copy(
        boardId = f.orKeep("boardId", boardId) { it as? String ?: boardId },
        name = f.orKeep("name", name) { it as? String ?: name },
        position = f.orKeep("position", position) { it.intOrNull() ?: position },
        wipLimit = f.orKeep("wipLimit", wipLimit) { it.intOrNull() },
        isDoneColumn = f.orKeep("isDoneColumn", isDoneColumn) { it as? Boolean ?: isDoneColumn },
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

// El servidor no manda createdAt en los registros que nacen de un envío: la versión del servidor lo sustituye.
private fun ServerRecord.createdAtMillis(): Long = fields["createdAt"].millis() ?: updatedAt.toEpochMilli()

// Fila nueva para un registro vivo que no existe en local. Una tarea sin forma conocida (ni lista ni columna) no es utilizable.
internal fun ServerRecord.toNewRow(me: String): SyncableEntity? {
    val created = createdAtMillis()
    return when (entity) {
        SyncEntity.LIST -> TaskListEntity(id, "", 0, created, updatedAt.toEpochMilli(), pending = false, deletedAt = null).adoptedFrom(this)
        SyncEntity.TASK -> {
            if ((fields["listId"] as? String) == null && (fields["columnId"] as? String) == null) return null
            TaskEntity(
                id = id, columnId = null, title = "", description = null, priority = DEFAULT_PRIORITY, dueAt = null,
                assigneeId = null, position = 0, createdAt = created, completedAt = null,
                updatedAt = updatedAt.toEpochMilli(), pending = false, deletedAt = null,
            ).adoptedFrom(this, me)
        }
        SyncEntity.BOARD -> BoardEntity(
            id = id, name = "", createdAt = created, updatedAt = updatedAt.toEpochMilli(), pending = false, deletedAt = null,
        ).adoptedFrom(this)
        SyncEntity.COLUMN -> {
            if ((fields["boardId"] as? String) == null) return null
            ColumnEntity(
                id = id, boardId = "", name = "", position = 0, wipLimit = null, isDoneColumn = false,
                createdAt = created, updatedAt = updatedAt.toEpochMilli(), pending = false, deletedAt = null,
            ).adoptedFrom(this)
        }
        SyncEntity.SUBTASK -> {
            if ((fields["taskId"] as? String) == null) return null
            SubtaskEntity(
                id = id, taskId = "", title = "", completed = false, position = 0, assigneeId = null, dueAt = null,
                createdAt = created, updatedAt = updatedAt.toEpochMilli(), pending = false, deletedAt = null,
            ).adoptedFrom(this, me)
        }
        else -> null
    }
}

internal fun SyncableEntity.adoptedFrom(record: ServerRecord, me: String): SyncableEntity = when (this) {
    is TaskListEntity -> adoptedFrom(record)
    is TaskEntity -> adoptedFrom(record, me)
    is SubtaskEntity -> adoptedFrom(record, me)
    is BoardEntity -> adoptedFrom(record)
    is ColumnEntity -> adoptedFrom(record)
    else -> error("Entidad no sincronizable: ${this::class.simpleName}")
}

private const val DEFAULT_PRIORITY = "MEDIUM"
