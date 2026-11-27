package com.simposio.taskeer.ui.components

import android.provider.Settings
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.simposio.taskeer.ui.theme.Motion

object CompletionMotion {
    const val SettleMillis: Long = Motion.SettleMillis
    const val BounceScale: Float = Motion.BounceScale
    const val BounceMillis: Int = Motion.BounceMillis
    const val StrikeMillis: Int = Motion.StrikeMillis
    const val DimAlpha: Float = Motion.DimAlpha
}

fun animationsEnabled(scale: Float): Boolean = scale != 0f

fun settleMillis(scale: Float): Long = if (animationsEnabled(scale)) CompletionMotion.SettleMillis else 0L

// Segunda lectura del ajuste, aparte de AppContainer.animationScale: los composables no tienen el contenedor
// y los ViewModels no deben depender de Context.
// La pausa y el trazo progresivo no son animaciones de Compose, así que no heredan solos el ajuste del sistema.
@Composable
internal fun rememberAnimationsEnabled(): Boolean {
    if (LocalInspectionMode.current) return true
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        animationsEnabled(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }
}

// Sin animación (escala 0) los specs son null: la fila se mueve, aparece y desaparece al instante.
// fadeIn = false: la fila recién insertada entra ya opaca (Mi día) y solo se anima la reubicación de las demás.
@Composable
internal fun LazyItemScope.rowItemModifier(fadeIn: Boolean = true): Modifier = when {
    !rememberAnimationsEnabled() -> Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
    fadeIn -> Modifier.animateItem()
    else -> Modifier.animateItem(fadeInSpec = null)
}

@Composable
internal fun LazyGridItemScope.rowItemModifier(): Modifier =
    if (rememberAnimationsEnabled()) {
        Modifier.animateItem()
    } else {
        Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
    }
