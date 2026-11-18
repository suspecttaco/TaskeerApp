package com.simposio.taskeer.domain.list

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskList
import com.simposio.taskeer.domain.model.afterLocalChange
import com.simposio.taskeer.domain.model.afterLocalDelete
import com.simposio.taskeer.domain.model.newClientId
import com.simposio.taskeer.domain.model.newSyncMetadata
import java.time.Instant

// Id de la lista que siembran la migración y onCreate; es el mismo en todos los dispositivos para que
// «Tareas» no se duplique al sincronizar (UUID.nameUUIDFromBytes("taskeer:list:default")). Solo identifica
// la siembra: «Tareas» se renombra y borra como cualquier lista y la lista por defecto no es una constante.
const val SEEDED_TASK_LIST_ID = "e1882473-b35a-3d7f-a6fa-43e4f940dc11"
const val SEEDED_TASK_LIST_NAME = "Tareas"

// Códigos locales: la API no tiene listas (ver alineación con el backend).
const val LIST_NAME_REQUIRED = "LIST_NAME_REQUIRED"
const val LIST_NAME_TOO_LONG = "LIST_NAME_TOO_LONG"
const val LIST_NAME_TAKEN = "LIST_NAME_TAKEN"
const val LIST_NOT_FOUND = "LIST_NOT_FOUND"
const val LIST_LAST_PROTECTED = "LIST_LAST_PROTECTED"
const val LIST_NAME_MAX_LENGTH = 100

// La lista por defecto es la activa de menor posición; createdAt e id desempatan de forma determinista.
fun defaultListOf(lists: List<TaskList>): TaskList? =
    lists.filter { !it.isDeleted }.minWithOrNull(compareBy({ it.position }, { it.createdAt }, { it.id }))

fun nextListPosition(lists: List<TaskList>): Int =
    (lists.filter { !it.isDeleted }.maxOfOrNull { it.position } ?: -1) + 1

// La lista elegida en pantalla, o la por defecto si ya no existe o no hay elegida.
fun effectiveListId(selectedId: String?, lists: List<TaskList>): String? =
    lists.firstOrNull { !it.isDeleted && it.id == selectedId }?.id ?: defaultListOf(lists)?.id

fun newTaskList(
    name: String,
    position: Int,
    now: Instant,
    clientId: String = newClientId(),
): DomainResult<TaskList> {
    val trimmed = name.trim()
    nameFailure(trimmed)?.let { return it }
    return DomainResult.Success(
        TaskList(
            name = trimmed,
            position = position,
            createdAt = now,
            sync = newSyncMetadata(now, clientId),
        ),
    )
}

fun TaskList.renamed(name: String, now: Instant): DomainResult<TaskList> {
    val trimmed = name.trim()
    nameFailure(trimmed)?.let { return it }
    return DomainResult.Success(copy(name = trimmed, sync = sync.afterLocalChange(now)))
}

fun TaskList.softDeleted(now: Instant): TaskList = copy(sync = sync.afterLocalDelete(now))

fun checkNameAvailable(name: String, existing: List<TaskList>, ignoringId: String? = null): DomainError? {
    val wanted = name.trim()
    val taken = existing.any { !it.isDeleted && it.id != ignoringId && it.name.trim().equals(wanted, ignoreCase = true) }
    return if (taken) DomainError.Conflict(LIST_NAME_TAKEN, "Ya existe una lista con ese nombre") else null
}

fun checkNotLast(lists: List<TaskList>, id: String): DomainError? =
    if (lists.none { !it.isDeleted && it.id != id }) {
        DomainError.Conflict(LIST_LAST_PROTECTED, "No se puede eliminar la última lista")
    } else {
        null
    }

fun movedToDefault(tasks: List<Task>, targetListId: String, startPosition: Int, now: Instant): List<Task> =
    tasks.sortedWith(compareBy({ it.position }, { it.createdAt }, { it.id })).mapIndexed { index, task ->
        task.copy(
            listId = targetListId,
            position = startPosition + index,
            sync = task.sync.afterLocalChange(now),
        )
    }

private fun nameFailure(trimmed: String): DomainResult.Failure? = when {
    trimmed.isEmpty() -> validation(LIST_NAME_REQUIRED, "El nombre de la lista es obligatorio")
    trimmed.length > LIST_NAME_MAX_LENGTH -> validation(
        LIST_NAME_TOO_LONG,
        "El nombre de la lista no puede superar los $LIST_NAME_MAX_LENGTH caracteres",
    )
    else -> null
}

private fun validation(code: String, message: String): DomainResult.Failure =
    DomainResult.Failure(DomainError.Validation(code, message))
