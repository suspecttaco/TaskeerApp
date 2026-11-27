package com.simposio.taskeer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
internal fun EmptyState(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(Spacing.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap, Alignment.CenterVertically),
    ) {
        DecorativeIcon(
            icon = icon,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = Spacing.EmptyIconSize,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    TaskeerTheme {
        EmptyState(
            icon = Icons.Filled.Inbox,
            text = "No hay tareas",
            actionLabel = "Añadir tarea",
            onAction = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
