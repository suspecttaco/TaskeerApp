package com.simposio.taskeer.ui.tasks

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.simposio.taskeer.ui.components.AppDivider
import com.simposio.taskeer.ui.components.rememberAnimationsEnabled
import com.simposio.taskeer.ui.navigation.editorRowEnter
import com.simposio.taskeer.ui.navigation.editorRowExit
import com.simposio.taskeer.ui.navigation.editorValueTransition

// El borrado real se difiere hasta que termina la animación de salida; si la fila sale de composición antes
// (giro, cambio de panel) también se ejecuta, para no perder lo que el usuario pidió.
@Composable
internal fun AnimatedSubtaskRow(
    id: String,
    isEditing: Boolean,
    initiallyVisible: Boolean,
    onExited: () -> Unit,
    content: @Composable (editing: Boolean, requestDelete: () -> Unit) -> Unit,
) {
    val animated = rememberAnimationsEnabled()
    val visible = remember(id) {
        MutableTransitionState(initiallyVisible).apply { targetState = true }
    }
    val currentOnExited = rememberUpdatedState(onExited)
    val exitedCalled = remember(id) { BooleanArray(1) }
    val finish = {
        if (!exitedCalled[0]) {
            exitedCalled[0] = true
            currentOnExited.value()
        }
    }

    AnimatedVisibility(
        visibleState = visible,
        enter = editorRowEnter(animated),
        exit = editorRowExit(animated),
    ) {
        Column {
            AnimatedContent(
                targetState = isEditing,
                transitionSpec = { editorValueTransition(animated) },
                label = "subtaskRow",
            ) { editing ->
                content(editing) { visible.targetState = false }
            }
            AppDivider()
        }
    }

    LaunchedEffect(visible.isIdle, visible.targetState) {
        if (visible.isIdle && !visible.targetState) finish()
    }
    DisposableEffect(visible) {
        onDispose { if (!visible.targetState) finish() }
    }
}
