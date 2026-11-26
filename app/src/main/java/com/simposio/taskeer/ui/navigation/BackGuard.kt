package com.simposio.taskeer.ui.navigation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

// La flecha de la barra superior navega sin pasar por BackHandler; esta guarda permite a una pantalla interceptarla.
class BackGuard {
    private var handler: (() -> Unit)? = null

    fun install(handler: () -> Unit) {
        this.handler = handler
    }

    fun clear() {
        handler = null
    }

    fun intercept(): Boolean {
        val current = handler ?: return false
        current()
        return true
    }
}

val LocalBackGuard: ProvidableCompositionLocal<BackGuard> = compositionLocalOf { BackGuard() }
