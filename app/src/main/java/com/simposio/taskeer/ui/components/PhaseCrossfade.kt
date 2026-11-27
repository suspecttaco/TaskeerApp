package com.simposio.taskeer.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.simposio.taskeer.ui.theme.phaseFadeSpec

internal enum class PhaseKind { LOADING, ERROR, CONTENT }

// EMPTY y LIST comparten CONTENT: pasar de una a otra es un cambio de datos, no de fase.
internal fun phaseKindOf(loading: Boolean, error: Boolean): PhaseKind = when {
    loading -> PhaseKind.LOADING
    error -> PhaseKind.ERROR
    else -> PhaseKind.CONTENT
}

@Composable
internal fun PhaseCrossfade(
    kind: PhaseKind,
    modifier: Modifier = Modifier,
    content: @Composable (PhaseKind) -> Unit,
) {
    Crossfade(
        targetState = kind,
        modifier = modifier,
        animationSpec = phaseFadeSpec(rememberAnimationsEnabled()),
        label = "phase",
        content = content,
    )
}

// El contenido que se desvanece sigue leyendo el estado vivo: al salir de ERROR el mensaje se vaciaría a mitad del fundido.
@Composable
internal fun <T> rememberWhile(active: Boolean, value: T): T {
    val holder = remember { Held(value) }
    if (active) holder.value = value
    return holder.value
}

private class Held<T>(var value: T)
