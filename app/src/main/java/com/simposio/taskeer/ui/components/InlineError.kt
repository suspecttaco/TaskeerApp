package com.simposio.taskeer.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
internal fun InlineError(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Preview(showBackground = true)
@Composable
private fun InlineErrorPreview() {
    TaskeerTheme { InlineError(message = "No se pudo guardar") }
}
