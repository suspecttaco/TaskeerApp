package com.simposio.taskeer.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.ui.theme.Spacing

@Composable
internal fun TaskPriority.color(): Color = when (this) {
    TaskPriority.LOW -> MaterialTheme.colorScheme.secondary
    TaskPriority.MEDIUM -> MaterialTheme.colorScheme.primary
    TaskPriority.HIGH -> MaterialTheme.colorScheme.tertiary
    TaskPriority.URGENT -> MaterialTheme.colorScheme.error
}

@StringRes
internal fun TaskPriority.labelRes(): Int = when (this) {
    TaskPriority.LOW -> R.string.task_priority_low
    TaskPriority.MEDIUM -> R.string.task_priority_medium
    TaskPriority.HIGH -> R.string.task_priority_high
    TaskPriority.URGENT -> R.string.task_priority_urgent
}

@Composable
internal fun PriorityDot(priority: TaskPriority, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.task_priority_description, stringResource(priority.labelRes()))
    Box(
        modifier = modifier
            .size(Spacing.DotSize)
            .background(priority.color(), CircleShape)
            .semantics { contentDescription = description },
    )
}
