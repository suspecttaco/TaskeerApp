package com.simposio.taskeer.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntSize

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

internal object EditorMotion {
    const val PanelFadeOutMillis = NavMotion.ExitFadeMillis
    const val PanelFadeInMillis = NavMotion.EnterFadeMillis
    const val PanelScale = NavMotion.ExitScale
    const val ValueFadeOutMillis = 90
    const val ValueFadeInMillis = 150
    const val SizeMillis = NavMotion.PanelEnterMillis
    const val RowEnterMillis = NavMotion.PanelEnterMillis
    const val RowExitMillis = NavMotion.PanelExitMillis
}

// La altura de la hoja es fija: el recorte por tamaño del AnimatedContent solo podría animar un salto que no existe.
private fun noSizeChange(): SizeTransform = SizeTransform(clip = false) { _, _ -> snap() }

internal fun editorPanelTransition(animated: Boolean): ContentTransform =
    if (!animated) {
        ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = noSizeChange())
    } else {
        ContentTransform(
            targetContentEnter = fadeIn(
                tween(EditorMotion.PanelFadeInMillis, delayMillis = EditorMotion.PanelFadeOutMillis),
            ) + scaleIn(
                tween(EditorMotion.PanelFadeInMillis, delayMillis = EditorMotion.PanelFadeOutMillis),
                initialScale = EditorMotion.PanelScale,
            ),
            initialContentExit = fadeOut(tween(EditorMotion.PanelFadeOutMillis)),
            sizeTransform = noSizeChange(),
        )
    }

internal fun editorValueTransition(animated: Boolean): ContentTransform =
    if (!animated) {
        ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = noSizeChange())
    } else {
        fadeIn(tween(EditorMotion.ValueFadeInMillis, delayMillis = EditorMotion.ValueFadeOutMillis)) togetherWith
            fadeOut(tween(EditorMotion.ValueFadeOutMillis))
    }

internal fun editorSizeSpec(animated: Boolean): FiniteAnimationSpec<IntSize> =
    if (!animated) snap() else tween(EditorMotion.SizeMillis)

internal fun editorRowEnter(animated: Boolean): EnterTransition =
    if (!animated) {
        EnterTransition.None
    } else {
        expandVertically(tween(EditorMotion.RowEnterMillis)) + fadeIn(tween(EditorMotion.RowEnterMillis))
    }

internal fun editorRowExit(animated: Boolean): ExitTransition =
    if (!animated) {
        ExitTransition.None
    } else {
        shrinkVertically(tween(EditorMotion.RowExitMillis)) + fadeOut(tween(EditorMotion.RowExitMillis))
    }
