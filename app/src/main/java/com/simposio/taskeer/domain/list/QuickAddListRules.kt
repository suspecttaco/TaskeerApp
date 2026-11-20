package com.simposio.taskeer.domain.list

import com.simposio.taskeer.domain.model.TaskListSummary

// Null solo mientras no hay listas cargadas: el repositorio resuelve entonces la lista por defecto.
fun effectiveListId(lists: List<TaskListSummary>, chosen: String?, contextListId: String?): String? =
    listOfNotNull(chosen, contextListId).firstOrNull { id -> lists.any { it.list.id == id } }
        ?: defaultListOf(lists.map { it.list })?.id
