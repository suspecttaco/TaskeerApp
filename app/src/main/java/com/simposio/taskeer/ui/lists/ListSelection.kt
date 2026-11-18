package com.simposio.taskeer.ui.lists

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Una sola instancia en el contenedor: la pantalla «Tareas» y el selector son dos ViewModels que
// comparten qué lista está abierta. Nulo significa «la lista por defecto», que se resuelve al leer.
class ListSelection(initial: String? = null) {
    private val state = MutableStateFlow(initial)

    val selectedListId: StateFlow<String?> = state.asStateFlow()

    fun select(listId: String?) {
        state.value = listId
    }
}
