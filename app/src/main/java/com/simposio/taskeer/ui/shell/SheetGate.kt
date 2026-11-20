package com.simposio.taskeer.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

// Cuenta las hojas modales abiertas para que la apertura automática de sugerencias no se apile sobre otra.
class SheetGate {
    var open: Int by mutableIntStateOf(0)
        private set

    val anyOpen: Boolean get() = open > 0

    fun opened() {
        open += 1
    }

    fun closed() {
        open = (open - 1).coerceAtLeast(0)
    }
}

val LocalSheetGate = compositionLocalOf { SheetGate() }

@Composable
fun TrackOpenSheet() {
    val gate = LocalSheetGate.current
    DisposableEffect(gate) {
        gate.opened()
        onDispose { gate.closed() }
    }
}
