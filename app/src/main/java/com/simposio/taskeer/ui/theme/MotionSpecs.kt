package com.simposio.taskeer.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.unit.Dp

internal fun phaseFadeSpec(animated: Boolean): FiniteAnimationSpec<Float> =
    if (!animated) snap() else tween(Motion.PhaseFadeMillis)

internal fun stateFloatSpec(animated: Boolean): FiniteAnimationSpec<Float> =
    if (!animated) snap() else tween(Motion.StateChangeMillis)

internal fun stateDpSpec(animated: Boolean): FiniteAnimationSpec<Dp> =
    if (!animated) snap() else tween(Motion.StateChangeMillis)

internal fun stateFadeEnter(animated: Boolean): EnterTransition =
    if (!animated) EnterTransition.None else fadeIn(tween(Motion.StateChangeMillis))

internal fun stateFadeExit(animated: Boolean): ExitTransition =
    if (!animated) ExitTransition.None else fadeOut(tween(Motion.StateChangeMillis))
