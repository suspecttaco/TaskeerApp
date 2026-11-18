package com.simposio.taskeer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.Checkbox
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
internal fun CompleteCheckbox(
    completed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = rememberAnimationsEnabled(),
) {
    val scale = remember { Animatable(1f) }
    var previouslyCompleted by remember { mutableStateOf(completed) }
    LaunchedEffect(completed) {
        if (completed && !previouslyCompleted && animate) {
            scale.animateTo(CompletionMotion.BounceScale, tween(CompletionMotion.BounceMillis / 3))
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
        previouslyCompleted = completed
    }
    val description = stringResource(
        if (completed) R.string.task_mark_pending else R.string.task_complete,
    )
    Checkbox(
        checked = completed,
        onCheckedChange = { onToggle() },
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .minimumInteractiveComponentSize()
            .semantics { contentDescription = description },
    )
}

@Preview(showBackground = true)
@Composable
private fun CompleteCheckboxPreview() {
    TaskeerTheme { CompleteCheckbox(completed = true, onToggle = {}) }
}
