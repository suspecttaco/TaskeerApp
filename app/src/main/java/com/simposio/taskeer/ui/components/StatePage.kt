package com.simposio.taskeer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

internal data class StatePageAction(val label: String, val onClick: () -> Unit)

@Composable
internal fun StatePage(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: StatePageAction? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap, Alignment.CenterVertically),
    ) {
        DecorativeIcon(
            icon = icon,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = Spacing.EmptyIconSize,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.let { FilledTonalButton(onClick = it.onClick) { Text(it.label) } }
    }
}

@Preview(showBackground = true)
@Composable
private fun StatePagePreview() {
    TaskeerTheme {
        StatePage(
            icon = Icons.Filled.SearchOff,
            title = "No encontrado",
            message = "Lo que buscas no existe o se eliminó",
            action = StatePageAction("Ir al inicio") {},
        )
    }
}
