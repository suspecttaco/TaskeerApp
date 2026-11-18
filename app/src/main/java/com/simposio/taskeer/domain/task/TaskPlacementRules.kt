package com.simposio.taskeer.domain.task

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult

// Códigos locales: la API no tiene listas.
const val TASK_PLACEMENT_CONFLICT = "TASK_PLACEMENT_CONFLICT"
const val TASK_IN_BOARD = "TASK_IN_BOARD"

sealed interface TaskPlacement {
    data class InColumn(val columnId: String) : TaskPlacement

    data class InList(val listId: String) : TaskPlacement
}

// defaultListId ya viene resuelto (lista de menor posición): esta regla no consulta el almacén.
fun resolvePlacement(columnId: String?, listId: String?, defaultListId: String): DomainResult<TaskPlacement> = when {
    columnId != null && listId != null -> DomainResult.Failure(
        DomainError.Validation(TASK_PLACEMENT_CONFLICT, "Una tarea no puede estar en una columna y en una lista a la vez"),
    )
    columnId != null -> DomainResult.Success(TaskPlacement.InColumn(columnId))
    else -> DomainResult.Success(TaskPlacement.InList(listId ?: defaultListId))
}

fun restoredPosition(original: Int, occupied: Set<Int>, activeCount: Int): Int =
    if (original !in occupied) original else activeCount
