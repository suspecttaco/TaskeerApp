package com.simposio.taskeer.ui.permission

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Cada ViewModel con permisos de calendario crea el suyo con las mismas costuras: la factoría no puede dar un
// ViewModel a otro, y la lectura del permiso es barata.
class CalendarAccessTracker(
    private val isReadGranted: () -> Boolean,
    private val isWriteGranted: () -> Boolean,
) {
    private val accessState = MutableStateFlow(currentAccess())
    private val answeredState = MutableStateFlow(false)

    val access: StateFlow<CalendarAccess> = accessState.asStateFlow()
    val answered: StateFlow<Boolean> = answeredState.asStateFlow()

    fun refresh() {
        accessState.value = currentAccess()
    }

    fun onResult() {
        answeredState.value = true
        refresh()
    }

    private fun currentAccess(): CalendarAccess =
        CalendarPermissionPolicy.accessOf(isReadGranted(), isWriteGranted())
}
