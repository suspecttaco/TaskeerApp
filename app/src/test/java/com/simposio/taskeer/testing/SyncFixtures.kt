package com.simposio.taskeer.testing

import com.simposio.taskeer.data.local.SubtaskEntity
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.data.local.TaskListEntity

const val USER_A = "11111111-1111-4111-8111-111111111111"
const val USER_B = "22222222-2222-4222-8222-222222222222"

fun syncList(
    id: String,
    name: String = "Tareas",
    position: Int = 0,
    updatedAt: Long = 1_000L,
    pending: Boolean = true,
    deletedAt: Long? = null,
) = TaskListEntity(id, name, position, createdAt = 0L, updatedAt = updatedAt, pending = pending, deletedAt = deletedAt)

fun syncTask(
    id: String,
    listId: String? = "list-1",
    columnId: String? = null,
    title: String = "Comprar leche",
    updatedAt: Long = 1_000L,
    pending: Boolean = true,
    deletedAt: Long? = null,
    assigneeId: String? = null,
    completedAt: Long? = null,
    repeatUnit: String? = null,
    repeatInterval: Int? = null,
    position: Int = 0,
) = TaskEntity(
    id = id,
    columnId = columnId,
    title = title,
    description = null,
    priority = "MEDIUM",
    dueAt = null,
    assigneeId = assigneeId,
    position = position,
    createdAt = 0L,
    completedAt = completedAt,
    updatedAt = updatedAt,
    pending = pending,
    deletedAt = deletedAt,
    repeatUnit = repeatUnit,
    repeatInterval = repeatInterval,
    listId = listId,
)

fun syncSubtask(
    id: String,
    taskId: String,
    updatedAt: Long = 1_000L,
    pending: Boolean = true,
    deletedAt: Long? = null,
) = SubtaskEntity(
    id = id,
    taskId = taskId,
    title = "Subtarea",
    completed = false,
    position = 0,
    assigneeId = null,
    dueAt = null,
    createdAt = 0L,
    updatedAt = updatedAt,
    pending = pending,
    deletedAt = deletedAt,
)

fun syncBoard(
    id: String,
    name: String = "Sprint",
    workspaceId: String? = null,
    updatedAt: Long = 1_000L,
    pending: Boolean = true,
    deletedAt: Long? = null,
) = com.simposio.taskeer.data.local.BoardEntity(id, name, createdAt = 0L, updatedAt = updatedAt, pending = pending, deletedAt = deletedAt, workspaceId = workspaceId)

fun syncColumn(
    id: String,
    boardId: String,
    name: String = "Por hacer",
    position: Int = 0,
    isDone: Boolean = false,
    updatedAt: Long = 1_000L,
    pending: Boolean = true,
    deletedAt: Long? = null,
) = com.simposio.taskeer.data.local.ColumnEntity(id, boardId, name, position, wipLimit = null, isDoneColumn = isDone, createdAt = 0L, updatedAt = updatedAt, pending = pending, deletedAt = deletedAt)
