package com.simposio.taskeer.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween

internal object NavMotion {
    const val ExitFadeMillis = 90
    const val EnterFadeMillis = 210
    const val ScaleMillis = 300
    const val ExitScale = 0.92f
    const val EnterScale = 1.08f
    const val PanelEnterMillis = 180
    const val PanelExitMillis = 120
}

// La entrada espera a que la saliente casi se haya fundido: así nunca se ven las dos pantallas a la vez.
internal fun zoomOutEnter(animationsEnabled: Boolean): EnterTransition =
    if (!animationsEnabled) {
        EnterTransition.None
    } else {
        fadeIn(tween(NavMotion.EnterFadeMillis, delayMillis = NavMotion.ExitFadeMillis)) +
            scaleIn(tween(NavMotion.ScaleMillis), initialScale = NavMotion.EnterScale)
    }

internal fun zoomOutExit(animationsEnabled: Boolean): ExitTransition =
    if (!animationsEnabled) {
        ExitTransition.None
    } else {
        fadeOut(tween(NavMotion.ExitFadeMillis)) +
            scaleOut(tween(NavMotion.ScaleMillis), targetScale = NavMotion.ExitScale)
    }

// Se anima la altura (no un desplazamiento): el borde inferior de la píldora, pegado al teclado, no se mueve.
internal fun panelEnter(animationsEnabled: Boolean): EnterTransition =
    if (!animationsEnabled) {
        EnterTransition.None
    } else {
        expandVertically(tween(NavMotion.PanelEnterMillis)) + fadeIn(tween(NavMotion.PanelEnterMillis))
    }

internal fun panelExit(animationsEnabled: Boolean): ExitTransition =
    if (!animationsEnabled) {
        ExitTransition.None
    } else {
        shrinkVertically(tween(NavMotion.PanelExitMillis)) + fadeOut(tween(NavMotion.PanelExitMillis))
    }
